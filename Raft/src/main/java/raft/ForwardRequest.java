package raft;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class ForwardRequest {
    public static OperationResponse forward(String leaderPort, OperationRequest request) {
        OperationResponse response;
        try {
            System.out.println("leaderPort: " + leaderPort);
            ManagedChannel channel = ManagedChannelBuilder.forTarget(leaderPort)
                    .usePlaintext()
                    .build();
            // For logging the forwarding, we use the same format.
            System.out.println("Forwarding: Node " + leaderPort + " sends RPC SubmitOperation to Leader Node " + leaderPort);
            RaftGrpc.RaftBlockingStub stub = RaftGrpc.newBlockingStub(channel);
            response = stub.submitOperation(request);
            channel.shutdown();
        } catch (Exception e) {
            System.out.println("Exception: " + e);
            response = OperationResponse.newBuilder().setResult("Error forwarding request to leader.").build();
        }
        return response;
    }
}
