package raft;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.Scanner;

public class RaftClient {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: RaftClient <nodeId> [operation]");
            System.exit(1);
        }
        // The first argument is the Java port, e.g., "60051"
        String leaderPort = args[0];
        int lp = Integer.parseInt(leaderPort);
        int containerPythonPort = lp - 10000; // e.g., 60051 -> 50051
        String containerName = "";
        if (containerPythonPort == 50051) {
            containerName = "raft-node-1";
        } else if (containerPythonPort == 50052) {
            containerName = "raft-node-2";
        } else if (containerPythonPort == 50053) {
            containerName = "raft-node-3";
        } else if (containerPythonPort == 50054) {
            containerName = "raft-node-4";
        } else if (containerPythonPort == 50055) {
            containerName = "raft-node-5";
        }
        String target = containerName + ":" + leaderPort;
        ManagedChannel channel = ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .build();
        RaftGrpc.RaftBlockingStub stub = RaftGrpc.newBlockingStub(channel);

        if (args.length >= 2) {
            String operation = args[1];
            System.out.println("Client sends RPC SubmitOperation to " + target);
            OperationResponse response = stub.submitOperation(OperationRequest.newBuilder()
                    .setOperation(operation)
                    .build());
            System.out.println("Response: " + response.getResult());
        } else {
            Scanner scanner = new Scanner(System.in);
            System.out.println("Enter an operation to submit:");
            while (scanner.hasNextLine()) {
                String operation = scanner.nextLine();
                System.out.println("Client sends RPC SubmitOperation to " + target);
                OperationResponse response = stub.submitOperation(OperationRequest.newBuilder()
                        .setOperation(operation)
                        .build());
                System.out.println("Response: " + response.getResult());
                System.out.println("Enter an operation to submit:");
            }
        }
        channel.shutdown();
    }
}
