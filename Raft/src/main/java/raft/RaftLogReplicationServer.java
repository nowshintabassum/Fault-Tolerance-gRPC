package raft;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class RaftLogReplicationServer {

    private static String nodeId;
    // Java nodes run on ports like 60051, 60052, etc.
    private static List<String> memberNodes = Arrays.asList("60051", "60052", "60053", "60054", "60055");
    private static String state = "follower";  // initially, all nodes are followers
    private static int currentTerm = 0;
    private static int commitIndex = 0;
    private static List<LogEntry> log = new ArrayList<>();
    private static ReentrantLock logLock = new ReentrantLock();
    private static String leaderId = null; // stores the leader's address (e.g., "localhost:60051")
    // A heartbeat timer: only running for the leader.
    private static Timer heartbeatTimer = null;

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 1) {
            System.err.println("Usage: RaftLogReplicationServer <nodeId>");
            System.exit(1);
        }
        nodeId = args[0]; // Expect a port, e.g., "60051"
        int port = Integer.parseInt(nodeId);

        // All nodes start as followers, and no leader is set initially.
        state = "follower";
        leaderId = "";

        Server server = ServerBuilder.forPort(port)
                .addService(new RaftLogReplicationServiceImpl())
                .build()
                .start();
        System.out.println("Node " + nodeId + " started on port " + port + " as " + state);

        // Do not start any heartbeat timer here;
        // if this node becomes leader via HandoffLeader RPC, it will start its own heartbeat timer.
        server.awaitTermination();
    }

    // This service implementation now supports AppendEntries, SubmitOperation, and the new HandoffLeader RPC.
    static class RaftLogReplicationServiceImpl extends RaftGrpc.RaftImplBase {

        @Override
        public void appendEntries(AppendEntriesRequest request, StreamObserver<AppendEntriesResponse> responseObserver) {
            System.out.println("Node " + nodeId + " runs RPC AppendEntries called by Leader Node " + request.getLeaderId());
            System.out.println("Term " + currentTerm);
            if (request.getTerm() >= currentTerm) {
                currentTerm = request.getTerm();
                logLock.lock();
                try {
                    // Overwrite the entire log with the leader’s log
                    log.clear();
                    log.addAll(request.getLogList());
                    // Execute pending operations up to commitIndex
                    if (request.getCommitIndex() > commitIndex) {
                        for (int i = commitIndex; i < request.getCommitIndex() && i < log.size(); i++) {
                            LogEntry entry = log.get(i);
                            executeOperation(entry);
                        }
                        commitIndex = request.getCommitIndex();
                    }
                } finally {
                    logLock.unlock();
                }
                leaderId = request.getLeaderId();
                state = "follower"; // ensure node remains follower
                AppendEntriesResponse response = AppendEntriesResponse.newBuilder()
                        .setTerm(currentTerm)
                        .setSuccess(true)
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            } else {
                System.out.println("Node " + nodeId + ": Received outdated AppendEntries.");
                AppendEntriesResponse response = AppendEntriesResponse.newBuilder()
                        .setTerm(currentTerm)
                        .setSuccess(false)
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        }

        @Override
        public void submitOperation(OperationRequest request, StreamObserver<OperationResponse> responseObserver) {
            System.out.println("Node " + nodeId + " runs RPC SubmitOperation called by Client");
            if (!state.equals("leader")) {
                // Forward request to the leader if this node isn't the leader.
                if (!leaderId.isEmpty()) {
                    System.out.println("Node " + nodeId + " forwards RPC SubmitOperation to Leader Node " + leaderId);
                    OperationResponse leaderResponse = ForwardRequest.forward(leaderId, request);
                    responseObserver.onNext(leaderResponse);
                    responseObserver.onCompleted();
                } else {
                    OperationResponse response = OperationResponse.newBuilder()
                            .setResult("No leader elected. Please try again later.")
                            .build();
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                }
                return;
            }
            // Leader: Append the new operation to the log.
            logLock.lock();
            int newIndex = log.size();
            LogEntry newEntry = LogEntry.newBuilder()
                    .setOperation(request.getOperation())
                    .setTerm(currentTerm)
                    .setIndex(newIndex)
                    .build();
            log.add(newEntry);
            logLock.unlock();
            System.out.println("Node " + nodeId + " appended log entry: " + newEntry);

            // Immediately respond to the client; the log replication and commit
            // (via heartbeat) will happen subsequently.
            OperationResponse response = OperationResponse.newBuilder()
                    .setResult("Operation appended to log. It will be replicated on the next heartbeat.")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void handoffLeader(ChangedLeader request, StreamObserver<ChangedLeaderAck> responseObserver) {
            System.out.println("Node " + nodeId + " received HandoffLeader RPC with newleader: " + request.getNewleader());
            // The newleader field is expected to be a string like "localhost:60051"
            String myAddress = "localhost:" + nodeId;
            if (request.getNewleader().equals(myAddress)) {
                // This node is the new leader
                state = "leader";
                leaderId = myAddress;
                currentTerm = request.getTerm();
                System.out.println("Node " + nodeId + " is now the leader after handoff.");
                // Start the heartbeat timer
                startHeartbeatTimer();
            } else {
                // Otherwise, this node becomes a follower and updates its known leader.
                state = "follower";
                leaderId = request.getNewleader();
                currentTerm = request.getTerm();
                System.out.println("Node " + nodeId + " acknowledges leader handoff to " + leaderId);
            }
            ChangedLeaderAck ack = ChangedLeaderAck.newBuilder().setAck(true).build();
            responseObserver.onNext(ack);
            responseObserver.onCompleted();
        }
    }

    // Helper function to simulate executing an operation.
    private static void executeOperation(LogEntry entry) {
        System.out.println("Node " + nodeId + " executed operation: " + entry.getOperation() + " at index: " + entry.getIndex());
    }

    // Starts a heartbeat timer that sends AppendEntries RPCs every 1 second while this node is the leader.
    private static void startHeartbeatTimer() {
        if (heartbeatTimer == null) {
            heartbeatTimer = new Timer();
            heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if (state.equals("leader")) {
                        sendHeartbeats();
                    }
                }
            }, 0, 1000);
            System.out.println("Heartbeat timer started on node " + nodeId);
        }
    }

    // The leader periodically sends heartbeats (containing the full log and commitIndex) to all followers.
    // If a majority acknowledges and there are pending uncommitted entries, the leader commits and executes them.
    private static void sendHeartbeats() {
        int ackCount = 1; // Count leader's own acknowledgment.
        for (String member : memberNodes) {
            if (member.equals(nodeId)) continue;
            try {
                io.grpc.ManagedChannel channel = io.grpc.ManagedChannelBuilder.forTarget("localhost:" + member)
                        .usePlaintext()
                        .build();
                RaftGrpc.RaftBlockingStub stub = RaftGrpc.newBlockingStub(channel);
                System.out.println("Node " + nodeId + " sends RPC AppendEntries to " + member);
                AppendEntriesResponse resp = stub.appendEntries(AppendEntriesRequest.newBuilder()
                        .setTerm(currentTerm)
                        .setLeaderId("localhost:" + nodeId)
                        .addAllLog(log)
                        .setCommitIndex(commitIndex)
                        .build());
                if (resp.getSuccess()) {
                    ackCount++;
                }
                channel.shutdown();
            } catch (Exception e) {
                System.err.println("Error sending heartbeat to node " + member + ": " + e.getMessage());
            }
        }
        // If a majority has acknowledged and there are pending log entries, commit them.
        if (ackCount > memberNodes.size() / 2) {
            logLock.lock();
            try {
                if (commitIndex < log.size()) {
                    // Execute all uncommitted entries.
                    for (int i = commitIndex; i < log.size(); i++) {
                        LogEntry entry = log.get(i);
                        executeOperation(entry);
                    }
                    commitIndex = log.size();
                    System.out.println("Node " + nodeId + " has committed and executed log entries up to index " + (commitIndex - 1));
                }
            } finally {
                logLock.unlock();
            }
        }
    }
}
