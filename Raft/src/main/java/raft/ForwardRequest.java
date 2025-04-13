package raft;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class ForwardRequest {
    public static OperationResponse forward(String leaderPort, OperationRequest request) {
        OperationResponse response;
        try {
            // int lp = Integer.parseInt(leaderPort);     // leaderPort, e.g., "60051"
            // int containerPythonPort = lp - 10000;         // e.g., 60051 -> 50051
            // String containerName = "";
            // if (containerPythonPort == 50051) {
            //     containerName = "raft-node-1";
            // } else if (containerPythonPort == 50052) {
            //     containerName = "raft-node-2";
            // } else if (containerPythonPort == 50053) {
            //     containerName = "raft-node-3";
            // } else if (containerPythonPort == 50054) {
            //     containerName = "raft-node-4";
            // } else if (containerPythonPort == 50055) {
            //     containerName = "raft-node-5";
            // }
            // String target = containerName + ":" + leaderPort;
            System.out.println("Forwarding: LeaderPort: " + leaderPort);
            ManagedChannel channel = ManagedChannelBuilder.forTarget(leaderPort)
                    .usePlaintext()
                    .build();
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
