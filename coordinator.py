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
    vote_results = []
    # Iterate over each participant address and send a vote request
    for participant in participants:
        # participant_address = 'localhost:' + participant 
        participant_address = participant
        try: 
            with grpc.insecure_channel(participant_address) as channel:
                stub = twopc_pb2_grpc.VotingPhaseStub(channel)
                
                # Coordinator log message before sending the vote request
                print(f"Phase Voting_Phase of Node1 (Coordinator) requests RPC RequestVote to Phase Voting_Phase of Participant Node {participant}")
                
                # Create and send a vote request (change "commit" to "abort" to simulate an abort)
                vote_request = twopc_pb2.VoteRequest(vote_request="request")
                response = stub.RequestVote(vote_request)
                
                # Log the response received from the participant
                print(f"Phase Voting_Phase of Node1 (Coordinator) received response {response.vote_commit} from Phase Voting_Phase of Partcipant Node {participant}")
                vote_results.append(response.vote_commit)
        except grpc.RpcError:
                response = False
                
                # Log the response received from the participant
                print(f"Phase Voting_Phase of Node1 (Coordinator) received response {response.vote_commit} from Phase Voting_Phase of Partcipant Node {participant}")
                vote_results.append(False)
                
    global_commit = all(vote_results)
    # print("Global decision computed:", "commit" if global_commit else "abort")
    
    
    # Handoff phase: invoke the Java DecisionCoordinator's RPC.
    # Assume the DecisionCoordinator service is running on localhost at port 60060 (for example).
    # decision_coordinator_address = 'localhost:60060'
    # decision_participant_addresses = ["localhost:" + str(int(p) + 10000) for p in participants]
    
    # Compute the participant addresses for the decision phase.
    # For each argument in the form "host:port", add 10000 to the port.
    decision_participant_addresses = []
    for p in participants:
        host, port_str = p.split(":")
        decision_port = int(port_str) + 10000
        decision_participant_addresses.append(f"{host}:{decision_port}")
    
    decision_coordinator_address = 'coordinator:60060'
    
    with grpc.insecure_channel(decision_coordinator_address) as channel:
        stub = twopc_pb2_grpc.DecisionCoordinatorServiceStub(channel)
        
        # Create a DecisionHandoffRequest message.]
        handoff_request = twopc_pb2.DecisionHandoffRequest(
            global_commit=global_commit,
            participant_addresses=decision_participant_addresses  # List of participant addresses (e.g., ["60051", "60052"])
        )
        print("Phase Decision phase of Node1 (Coordinator) sends RPC Global Decision to own(Coordinator) Java service ")
        # print("Invoking DecisionCoordinatorService.startDecisionPhase with global decision and participant addresses:", handoff_request)
        handoff_response = stub.startDecisionPhase(handoff_request)
        # print("Received response from DecisionCoordinatorService:", handoff_response.message)
    
    

if __name__ == '__main__':
    run()
