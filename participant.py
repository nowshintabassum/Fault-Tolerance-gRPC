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
    def RequestVote(self, request, context):
        # Participant log message
        print(f"Phase Voting_Phase of Node2 recieves RPC RequestVote from Phase Voting_Phase of Node 1")
        
        # Here you decide whether to commit or abort.
        # For example, commit if the vote_request message is "commit", else abort.
        vote_commit = True
        
        return twopc_pb2.VoteResponse(vote_commit=vote_commit)

def serve(address):
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    twopc_pb2_grpc.add_VotingPhaseServicer_to_server(VotingPhaseServicer(), server)
    node_address = '[::]:'+address
    server.add_insecure_port(node_address)
    server.start()
    print(f"Participant server started at {node_address}. Waiting for vote requests...")
    server.wait_for_termination()

if __name__ == '__main__':
    if len(sys.argv) != 2:
        print("Usage: python participant.py <address>")
        sys.exit(1)
    
    # The address (for example, "localhost:50051") is taken from the command line arguments
    address = sys.argv[1]
    serve(address)
