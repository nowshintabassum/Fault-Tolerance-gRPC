import grpc
import sys
from concurrent import futures
import time
import twopc_pb2
import twopc_pb2_grpc

# Define node and phase details
NODE_ID = 2
PHASE_NAME = "Vote"

class VotingPhaseServicer(twopc_pb2_grpc.VotingPhaseServicer):
    def __init__(self, node_address):
        """
        node_address: e.g. "50051"
        """
        self.node_address = node_address
        
    def RequestVote(self, request, context):
        # Participant log message
        print(f"Phase Voting_Phase of Node {self.node_address} recieves RPC RequestVote from Phase Voting_Phase of Node Coordinator")
        
        #if participant up and running, sends True to vote request
        vote_commit = True
        
        return twopc_pb2.VoteResponse(vote_commit=vote_commit)

def serve(address):
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    twopc_pb2_grpc.add_VotingPhaseServicer_to_server(VotingPhaseServicer(node_address = address), server)
    node_address = '[::]:'+address
    server.add_insecure_port(node_address)
    server.start()
    print(f"Participant server started at {node_address}. Waiting for vote requests...")
    server.wait_for_termination()

if __name__ == '__main__':
    if len(sys.argv) != 2:
        print("Usage: python participant.py <address>")
        sys.exit(1)
    
    # The address (for example, "50051") is taken from the command line arguments
    address = sys.argv[1]
    serve(address)
