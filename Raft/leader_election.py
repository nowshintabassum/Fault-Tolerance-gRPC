import grpc
import random
import time
import threading
import raft_pb2
import raft_pb2_grpc
from concurrent import futures
import os
import argparse

# Delay startup to allow dependent containers to be ready.
time.sleep(30)

HEARTBEAT_INTERVAL = 1.0
ELECTION_TIMEOUT = random.uniform(1.5, 3.0)
STATE = "follower"
CURRENT_TERM = 0
VOTES_RECEIVED = 0
VOTED_FOR = None
# List of Python ports for nodes.
MEMBER_NODES = ['50051', '50052', '50053', '50054', '50055']
HANDED_OFF_LEADER = False
LAST_HEARTBEAT = time.time()

# Mapping from Python node IDs to container names (using hyphens).
NODE_TO_CONTAINER = {
    "50051": "raft-node-1",
    "50052": "raft-node-2",
    "50053": "raft-node-3",
    "50054": "raft-node-4",
    "50055": "raft-node-5"
}

class RaftServicer(raft_pb2_grpc.RaftServicer):
    def __init__(self, node_id):
        self.node_id = node_id

    def RequestVote(self, request, context):
        global CURRENT_TERM, VOTED_FOR, STATE
        print(f"Python Node {self.node_id} runs RPC RequestVote called by Node {request.candidateId}")
        if request.term > CURRENT_TERM:
            CURRENT_TERM = request.term
            VOTED_FOR = None
        vote_granted = False
        if request.term == CURRENT_TERM and (VOTED_FOR is None or VOTED_FOR == request.candidateId):
            VOTED_FOR = request.candidateId
            vote_granted = True
        return raft_pb2.VoteResponse(term=CURRENT_TERM, voteGranted=vote_granted)

    def AppendEntries(self, request, context):
        global CURRENT_TERM, STATE, LAST_HEARTBEAT, HANDED_OFF_LEADER
        print(f"Python Node {self.node_id} runs RPC AppendEntries called by Leader Node {request.leaderId}")
        print("Term", request.term)
        if request.term >= CURRENT_TERM:
            CURRENT_TERM = request.term
            STATE = "follower"
            LAST_HEARTBEAT = time.time()
            HANDED_OFF_LEADER = False
            return raft_pb2.AppendEntriesResponse(term=CURRENT_TERM, success=True)
        else:
            print("Initiating leader election again")
            return raft_pb2.AppendEntriesResponse(term=CURRENT_TERM, success=False)

def send_request_vote(NODE_ID):
    global VOTES_RECEIVED
    VOTES_RECEIVED = 1  # vote for self
    for node in MEMBER_NODES:
        if NODE_ID == node:
            continue
        try:
            # Use the container name from our mapping; the Python service listens on the same port.
            container = NODE_TO_CONTAINER[node]
            target = f"{container}:{node}"
            print(f"Python Node {NODE_ID} sends RPC RequestVote to {target}")
            channel = grpc.insecure_channel(target)
            stub = raft_pb2_grpc.RaftStub(channel)
            response = stub.RequestVote(raft_pb2.VoteRequest(term=CURRENT_TERM, candidateId=NODE_ID))
            print(response)
            if response.voteGranted:
                VOTES_RECEIVED += 1
        except grpc.RpcError:
            continue
    print(f"Received {VOTES_RECEIVED} votes")

def send_heartbeats(NODE_ID):
    global HANDED_OFF_LEADER
    if not HANDED_OFF_LEADER:
        handoff_leader(NODE_ID)
        HANDED_OFF_LEADER = True
    for node in MEMBER_NODES:
        if NODE_ID == node:
            continue
        try:
            container = NODE_TO_CONTAINER[node]
            target = f"{container}:{node}"
            print(f"Python Node {NODE_ID} sends RPC AppendEntries to {target}")
            channel = grpc.insecure_channel(target)
            stub = raft_pb2_grpc.RaftStub(channel)
            stub.AppendEntries(raft_pb2.AppendEntriesRequest(term=CURRENT_TERM, leaderId=NODE_ID))
        except:
            continue

def run_election(NODE_ID):
    global STATE, CURRENT_TERM, VOTED_FOR
    STATE = "candidate"
    CURRENT_TERM += 1
    VOTED_FOR = NODE_ID
    print(f"Python Node {NODE_ID} starting election for term {CURRENT_TERM}")
    send_request_vote(NODE_ID)
    if VOTES_RECEIVED > len(MEMBER_NODES) // 2:
        STATE = "leader"
        print(f"Python Node {NODE_ID} becomes leader for term {CURRENT_TERM}")
        while STATE == "leader":
            send_heartbeats(NODE_ID)
            time.sleep(HEARTBEAT_INTERVAL)
    else:
        STATE = "follower"
        print(f"Python Node {NODE_ID} failed to become leader, reverting to follower")

def election_timer(NODE_ID):
    global STATE, ELECTION_TIMEOUT
    while True:
        if STATE in ["leader", "candidate"]:
            continue
        if time.time() - LAST_HEARTBEAT >= ELECTION_TIMEOUT:
            run_election(NODE_ID)
            ELECTION_TIMEOUT = random.uniform(1.5, 3.0)

def handoff_leader(new_leader_id):
    try:
        # Compute the corresponding Java port (Python port + 10000).
        java_port = str(int(new_leader_id) + 10000)
        # Use the mapping to get the container name.
        container = NODE_TO_CONTAINER[new_leader_id]
        # The Java service runs on port java_port.
        java_server_address = f"{container}:{java_port}"
        print(f"Python Node {new_leader_id} contacting Java server at {java_server_address} for handoff")
        channel = grpc.insecure_channel(java_server_address)
        stub = raft_pb2_grpc.RaftStub(channel)
        request = raft_pb2.ChangedLeader(newleader=java_server_address, term=CURRENT_TERM)
        response = stub.HandoffLeader(request)
        print(f"Python Node {new_leader_id} sent HandoffLeader to {java_server_address} on Term {CURRENT_TERM}")
    except grpc.RpcError as e:
        print(f"Failed to contact Java server: {e.code()} - {e.details()}")

def serve(NODE_ID):
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    raft_pb2_grpc.add_RaftServicer_to_server(RaftServicer(node_id=NODE_ID), server)
    port = NODE_ID
    server.add_insecure_port(f"[::]:{port}")
    server.start()
    print(f"Python Node {NODE_ID} started on port {port} as {STATE}")
    election_timer(NODE_ID)
    server.wait_for_termination()

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--node_id", type=int, required=True, help="Own node ID")
    args = parser.parse_args()
    node_id = args.node_id
    serve(str(node_id))
