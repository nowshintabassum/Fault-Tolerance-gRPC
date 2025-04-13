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
    private static String state = "follower";
    private static int currentTerm = 0;
    private static int commitIndex = 0;
    private static List<LogEntry> log = new ArrayList<>();
    private static ReentrantLock logLock = new ReentrantLock();
    // leaderId will be in the format "raft_node_X:6005Y"
    private static String leaderId = null;
    private static Timer heartbeatTimer = null;

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 1) {
            System.err.println("Usage: RaftLogReplicationServer <nodeId>");
            System.exit(1);
        }
        // In the Java service, nodeId is the service port (e.g., "60051")
        nodeId = args[0];
        int port = Integer.parseInt(nodeId);
        state = "follower";
        leaderId = "";
        Server server = ServerBuilder.forPort(port)
                .addService(new RaftLogReplicationServiceImpl())
                .build()
                .start();
        System.out.println("Java Node " + nodeId + " started on port " + port + " as " + state);
        server.awaitTermination();
    }

    static class RaftLogReplicationServiceImpl extends RaftGrpc.RaftImplBase {

        @Override
        public void appendEntries(AppendEntriesRequest request, StreamObserver<AppendEntriesResponse> responseObserver) {
            System.out.println("Java Node " + nodeId + " runs RPC AppendEntries called by Leader Node " + request.getLeaderId());
            System.out.println("Term " + currentTerm);
            if (request.getTerm() >= currentTerm) {
                currentTerm = request.getTerm();
                logLock.lock();
                try {
                    log.clear();
                    log.addAll(request.getLogList());
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
                state = "follower";
                AppendEntriesResponse response = AppendEntriesResponse.newBuilder()
                        .setTerm(currentTerm)
                        .setSuccess(true)
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            } else {
                System.out.println("Java Node " + nodeId + ": Received outdated AppendEntries.");
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
            System.out.println("Java Node " + nodeId + " runs RPC SubmitOperation called by Client");
            if (!state.equals("leader")) {
                if (leaderId != null && !leaderId.isEmpty()) {
                    System.out.println("Java Node " + nodeId + " forwards RPC SubmitOperation to Leader Node " + leaderId);
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
            logLock.lock();
            int newIndex = log.size();
            LogEntry newEntry = LogEntry.newBuilder()
                    .setOperation(request.getOperation())
                    .setTerm(currentTerm)
                    .setIndex(newIndex)
                    .build();
            log.add(newEntry);
            logLock.unlock();
            System.out.println("Java Node " + nodeId + " appended log entry: " + newEntry);
            OperationResponse response = OperationResponse.newBuilder()
                    .setResult("Operation appended to log. It will be replicated on the next heartbeat.")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void handoffLeader(ChangedLeader request, StreamObserver<ChangedLeaderAck> responseObserver) {
            System.out.println("Java Node " + nodeId + " received HandoffLeader RPC with newleader: " + request.getNewleader());
            // Compute our container name based on our Python node.
            int javaPort = Integer.parseInt(nodeId);           // For example, 60051
            int pythonPort = javaPort - 10000;                   // For example, 50051
            String containerName = "";
            if (pythonPort == 50051) {
                containerName = "raft-node-1";
            } else if (pythonPort == 50052) {
                containerName = "raft-node-2";
            } else if (pythonPort == 50053) {
                containerName = "raft-node-3";
            } else if (pythonPort == 50054) {
                containerName = "raft-node-4";
            } else if (pythonPort == 50055) {
                containerName = "raft-node-5";
            }
            String myServiceName = containerName + ":" + nodeId;
            if (request.getNewleader().equals(myServiceName)) {
                state = "leader";
                leaderId = myServiceName;
                currentTerm = request.getTerm();
                System.out.println("Java Node " + nodeId + " is now the leader after handoff.");
                startHeartbeatTimer();
            } else {
                state = "follower";
                leaderId = request.getNewleader();
                currentTerm = request.getTerm();
                System.out.println("Java Node " + nodeId + " acknowledges leader handoff to " + leaderId);
            }
            ChangedLeaderAck ack = ChangedLeaderAck.newBuilder().setAck(true).build();
            responseObserver.onNext(ack);
            responseObserver.onCompleted();
        }
    }

    private static void executeOperation(LogEntry entry) {
        System.out.println("Java Node " + nodeId + " executed operation: " + entry.getOperation() +
                " at index: " + entry.getIndex());
    }

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
            System.out.println("Heartbeat timer started on Java Node " + nodeId);
        }
    }

    private static void sendHeartbeats() {
        int ackCount = 1; // Count leader's own acknowledgment.
        for (String member : memberNodes) {
            if (member.equals(nodeId)) continue;
            try {
                // For a peer, member is a Java port (e.g., "60052").
                int peerJavaPort = Integer.parseInt(member);
                int pythonPort = peerJavaPort - 10000;
                String containerName = "";
                if (pythonPort == 50051) {
                    containerName = "raft-node-1";
                } else if (pythonPort == 50052) {
                    containerName = "raft-node-2";
                } else if (pythonPort == 50053) {
                    containerName = "raft-node-3";
                } else if (pythonPort == 50054) {
                    containerName = "raft-node-4";
                } else if (pythonPort == 50055) {
                    containerName = "raft-node-5";
                }
                String target = containerName + ":" + member; // e.g., "raft_node_2:60052"
                System.out.println("Java Node " + nodeId + " sends RPC AppendEntries to " + target);
                io.grpc.ManagedChannel channel = io.grpc.ManagedChannelBuilder.forTarget(target)
                        .usePlaintext()
                        .build();
                RaftGrpc.RaftBlockingStub stub = RaftGrpc.newBlockingStub(channel);
                AppendEntriesResponse resp = stub.appendEntries(AppendEntriesRequest.newBuilder()
                        .setTerm(currentTerm)
                        // .setLeaderId("raft_node_" + (Integer.parseInt(nodeId) - 10000) + ":" + nodeId)
                        .setLeaderId(leaderId)
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
        if (ackCount > memberNodes.size() / 2) {
            logLock.lock();
            try {
                if (commitIndex < log.size()) {
                    for (int i = commitIndex; i < log.size(); i++) {
                        LogEntry entry = log.get(i);
                        executeOperation(entry);
                    }
                    commitIndex = log.size();
                    System.out.println("Java Node " + nodeId + " has committed and executed log entries up to index " + (commitIndex - 1));
                }
            } finally {
                logLock.unlock();
            }
        }
    }
}
