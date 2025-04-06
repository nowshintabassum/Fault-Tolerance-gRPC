import grpc
import random
import time
import threading
import raft_pb2
import raft_pb2_grpc
from concurrent import futures
import os
import argparse
time.sleep(3)
HEARTBEAT_INTERVAL = 1.0
ELECTION_TIMEOUT = random.uniform(1.5, 3.0)
STATE = "follower"
CURRENT_TERM = 0
VOTES_RECEIVED = 0
VOTED_FOR = None
MEMBER_NODES = ['50051', '50052', "50053"]
# , "50054", "50055"]
LAST_HEARTBEAT = time.time()



class RaftServicer(raft_pb2_grpc.RaftServicer):
    def __init__(self, node_id):
        """
        node_address: e.g. "50051"
        """
        self.node_id = node_id
    def RequestVote(self, request,context):
        vote_granted = False
        global CURRENT_TERM, VOTED_FOR, STATE
        print(f"Node {self.node_id} runs RPC RequestVote called by Node {request.candidateId}")

        if request.term > CURRENT_TERM:
            CURRENT_TERM = request.term
            VOTED_FOR = None
        

        vote_granted = False
        if request.term == CURRENT_TERM and (VOTED_FOR is None or VOTED_FOR == request.candidateId):
            VOTED_FOR = request.candidateId
            vote_granted = True

        return raft_pb2.VoteResponse(term=CURRENT_TERM, voteGranted=vote_granted)

    def AppendEntries(self, request, context):
        global CURRENT_TERM, STATE, LAST_HEARTBEAT
        print(f"Node {self.node_id} runs RPC AppendEntries called by Leader Node {request.leaderId}")

        print('Term', request.term)
        if request.term >= CURRENT_TERM:
            CURRENT_TERM = request.term
            STATE = "follower"
            LAST_HEARTBEAT = time.time()
            return raft_pb2.AppendEntriesResponse(term=CURRENT_TERM, success=True)
        else:
            print('Intiating leader election again')
            return raft_pb2.AppendEntriesResponse(term=CURRENT_TERM, success=False)

def send_request_vote(NODE_ID):
    global VOTES_RECEIVED
    VOTES_RECEIVED = 1  # vote for self
    for node in MEMBER_NODES:
        #skips itself
        if NODE_ID==node:
            continue
        try:
            print(f"Node {NODE_ID} sends RPC RequestVote to {node}")
            channel = grpc.insecure_channel(f"localhost:{node}")
            stub = raft_pb2_grpc.RaftStub(channel)
            response = stub.RequestVote(raft_pb2.VoteRequest(term=CURRENT_TERM, candidateId=NODE_ID))
            print(response)
            if response.voteGranted:
                VOTES_RECEIVED += 1
        except grpc.RpcError as e:
            continue
    print(f"Recieved {VOTES_RECEIVED} votes")
    # time.sleep(5) /

def send_heartbeats(NODE_ID):
    for node in MEMBER_NODES:
        if NODE_ID == node:
            continue
        try:
            print(f"Node {NODE_ID} sends RPC AppendEntries to {node}")
            channel = grpc.insecure_channel(f"localhost:{node}")
            stub = raft_pb2_grpc.RaftStub(channel)
            stub.AppendEntries(raft_pb2.AppendEntriesRequest(term=CURRENT_TERM, leaderId=NODE_ID))
        except:
            continue

def run_election(NODE_ID):
    global STATE, CURRENT_TERM, VOTED_FOR
    STATE = "candidate"
    CURRENT_TERM += 1
    #voting for itself
    VOTED_FOR = NODE_ID
    print(f"Node {NODE_ID} starting election for term {CURRENT_TERM}")

    send_request_vote(NODE_ID)
    if VOTES_RECEIVED > len(MEMBER_NODES) // 2:
        STATE = "leader"
        print(f"Node {NODE_ID} becomes leader for term {CURRENT_TERM}")
        while STATE == "leader":
            send_heartbeats(NODE_ID)
            time.sleep(HEARTBEAT_INTERVAL)
    else:
        STATE = "follower"
        print(f"Node {NODE_ID} failed to become leader, reverting to follower")



def election_timer(NODE_ID):
    global STATE, ELECTION_TIMEOUT
    
    while True:
        time.sleep(0.2)
        
        if STATE == "leader" or STATE == "candidate":
            continue
        if time.time() - LAST_HEARTBEAT >= ELECTION_TIMEOUT:
            run_election(NODE_ID)
            ELECTION_TIMEOUT = random.uniform(1.5, 3.0)


def serve(NODE_ID):
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    raft_pb2_grpc.add_RaftServicer_to_server(RaftServicer(node_id=NODE_ID), server)
    port = NODE_ID
    server.add_insecure_port(f"[::]:{port}")
    server.start()
    print(f"Node {NODE_ID} started on port {port} as {STATE}")
    
    election_timer(NODE_ID)
    # election_thread = threading.Thread(target=election_timer)
    # election_thread.daemon = True
    # election_thread.start()
    server.wait_for_termination()

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--node_id", type=int, required=True, help="Own node ID")
    args = parser.parse_args()
    node_id = args.node_id
    serve(str(node_id))