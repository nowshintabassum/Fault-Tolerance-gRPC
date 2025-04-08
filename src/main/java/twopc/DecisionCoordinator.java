package twopc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class DecisionCoordinator extends DecisionCoordinatorServiceGrpc.DecisionCoordinatorServiceImplBase {
    // NODE_ID will be set to the port number specified through CLI.
    private static int NODE_ID;
    private static final String PHASE_NAME = "Decision_Phase";

    @Override
    public void startDecisionPhase(DecisionHandoffRequest request, StreamObserver<DecisionHandoffResponse> responseObserver) {
        boolean globalCommit = request.getGlobalCommit();
        List<String> participants = request.getParticipantAddressesList();

        System.out.println("Phase " + PHASE_NAME + " of Node " + NODE_ID +
                " received RPC Global Decision from its own Voting Phase: " + (globalCommit ? "commit" : "abort"));
        System.out.println("Disseminating decision to participants: " + participants);

        // For each participant, creating a channel and send the global decision.
        for (String participant : participants) {
                try {
                ManagedChannel channel = ManagedChannelBuilder.forTarget(participant)
                        .usePlaintext()
                        .build();
                DecisionPhaseGrpc.DecisionPhaseBlockingStub stub = DecisionPhaseGrpc.newBlockingStub(channel);

                System.out.println("Phase " + PHASE_NAME + " of Node " + NODE_ID +
                        " sends RPC GlobalDecision to Participant " + participant);
                DecisionRequest decisionRequest = DecisionRequest.newBuilder()
                        .setGlobalCommit(globalCommit)
                        .build();
                DecisionResponse decisionResponse = stub.globalDecision(decisionRequest);
                System.out.println("Received ack from Participant " + participant + ": " + decisionResponse.getAck());
                channel.shutdown();
                } catch (Exception e) {
                // On error, continue without printing any messages.
                continue;
                }
        }

        // Respond to the caller.
        DecisionHandoffResponse response = DecisionHandoffResponse.newBuilder()
                .setMessage("Global decision disseminated to all participants")
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        
        if (args.length != 1) {
            System.out.println("Usage: java twopc.DecisionCoordinator <port>");
            System.exit(1);
        }
        int port = Integer.parseInt(args[0]);
        NODE_ID = port;  

        // Start the gRPC server on the specified port.
        Server server = ServerBuilder.forPort(port)
                .addService(new DecisionCoordinator())
                .build()
                .start();
        System.out.println("DecisionCoordinator gRPC service started on port " + port +
                ". NODE_ID set to " + NODE_ID);
        server.awaitTermination();
    }
}
