package twopc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import twopc.DecisionPhaseGrpc;
import twopc.DecisionRequest;
import twopc.DecisionResponse;

public class DecisionParticipant {
    // NODE_ID will now be set dynamically based on the port provided at runtime.
    private static int NODE_ID;
    private static final String PHASE_NAME = "Decision_Phase";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: java twopc.DecisionParticipant <port>");
            System.exit(1);
        }
        int port = Integer.parseInt(args[0]);
        NODE_ID = port;  // Use the port number as the node identifier

        Server server = ServerBuilder.forPort(port)
                .addService(new DecisionPhaseServiceImpl())
                .build()
                .start();
        System.out.println("Participant DecisionPhase server started at port " + port + ". Waiting for global decision...");

        server.awaitTermination();
    }

    static class DecisionPhaseServiceImpl extends DecisionPhaseGrpc.DecisionPhaseImplBase {
        @Override
        public void globalDecision(DecisionRequest request, StreamObserver<DecisionResponse> responseObserver) {
            // Log on the server side: when receiving the global decision from coordinator node 1.
            System.out.println("Phase " + PHASE_NAME + " of Node " + NODE_ID +
                    " receives RPC GlobalDecision from Phase Decision_Phase of Node 1");

            // Process the decision
            boolean globalCommit = request.getGlobalCommit();
            if (globalCommit) {
                System.out.println("Participant Node " + NODE_ID + " commits the transaction locally.");
            } else {
                System.out.println("Participant Node " + NODE_ID + " aborts the transaction locally.");
            }

            // Respond back with an acknowledgment message.
            DecisionResponse response = DecisionResponse.newBuilder()
                    .setAck("Global decision (" + (globalCommit ? "commit" : "abort") + ") received")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}
