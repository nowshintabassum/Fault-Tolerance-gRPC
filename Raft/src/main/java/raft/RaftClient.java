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
        // The first argument is the port (node id) to which the client initially connects.
        String target = args[0];
        ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:" + target)
                .usePlaintext()
                .build();
        RaftGrpc.RaftBlockingStub stub = RaftGrpc.newBlockingStub(channel);

        if (args.length >= 2) {
            // If an operation is specified as a second argument, submit it immediately.
            String operation = args[1];
            System.out.println("Node " + target + " sends RPC SubmitOperation to Node " + target);
            OperationResponse response = stub.submitOperation(OperationRequest.newBuilder()
                    .setOperation(operation)
                    .build());
            System.out.println("Response: " + response.getResult());
        } else {
            // Otherwise, use interactive mode.
            Scanner scanner = new Scanner(System.in);
            System.out.println("Enter an operation to submit:");
            while (scanner.hasNextLine()) {
                String operation = scanner.nextLine();
                System.out.println("Node " + target + " sends RPC SubmitOperation to Node " + target);
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
