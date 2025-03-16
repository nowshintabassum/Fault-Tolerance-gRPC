import grpc
import sys
import twopc_pb2
import twopc_pb2_grpc

# Define node and phase details
NODE_ID = 1
PHASE_NAME = "Vote"

def run():
    if len(sys.argv) < 2:
        print("Usage: python coordinator.py <participant_address_1> [participant_address_2 ...]")
        sys.exit(1)
    
    participants = sys.argv[1:]
    
    # Iterate over each participant address and send a vote request
    for participant in participants:
        participant_address = 'localhost:' + participant 
        try: 
            with grpc.insecure_channel(participant_address) as channel:
                stub = twopc_pb2_grpc.VotingPhaseStub(channel)
                
                # Coordinator log message before sending the vote request
                print(f"Phase Voting_Phase of Node1 (Coordinator) requests RPC RequestVote to Phase Voting_Phase of Participant Node {participant}")
                
                # Create and send a vote request (change "commit" to "abort" to simulate an abort)
                vote_request = twopc_pb2.VoteRequest(vote_request="request")
                response = stub.RequestVote(vote_request)
                
                # Log the response received from the participant
                print(f"Phase Voting_Phase of Node1 (Coordinator) received response from Phase Voting_Phase of Partcipant Node {participant}: Vote Commit = {response.vote_commit}")
        except grpc.RpcError:
                response = False
                
                # Log the response received from the participant
                print(f"Phase Voting_Phase of Node1 (Coordinator) received response from Phase Voting_Phase of Partcipant Node {participant}: Vote Commit = False")

if __name__ == '__main__':
    run()
