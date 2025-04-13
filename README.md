# Fault Tolerance gRPC

To download the assignment, clone the repository:

```console
git clone https://github.com/nowshintabassum/Fault-Tolerance-gRPC.git
```

## 2PC 

Install the necessary modules:

```console
pip install -r requirements.txt
```

The `twopc.proto` file is defined in the current (main) folder.

#### Building gRPC Code:

#### Java:
To build the Java code from the `.proto` file, run:


```
./gradlew clean build
```



#### Python:
To build the Python code from the `.proto` file, run:

```
python -m grpc_tools.protoc -I. --python_out=. --grpc_python_out=. twopc.proto
```

#### Coordinator and Participant Locations:

##### For Voting Phase
- The `coordinator.py` and `participant.py` files are located in:
`./`

##### For Decision Phase:
- The `DecisionCoordinator.java` and `DecisionParticipant.java` files are located under:
`src/main/java/twopc` folder


#### Dockerization:

All phases have been containerized using Docker. The corresponding Dockerfiles are:

- `Dockerfile.coordinator`
- `Dockerfile.participant`

To run all containers, a `docker-compose.yml` file is provided.

#### Running the Containers:

```console
docker-compose up --build
```


### Running the Code Without Docker-Compose:

#### Voting Phase
Participant:
```console
python participant.py 50051
```
Coordinator:
```console
python coordinator.py 50051 50052 50053 50054 50055
```
Java Coordinator:
```console
./gradlew runCoordinator --args='60060'
```
Java Participant:
```console
./gradlew runParticipant --args='60051'
./gradlew runParticipant --args='60052' and so on
```

It is recommended to run the containerized version of the nodes instead of executing them locally, as the ports are properly managed within the containers.

## Raft

Navigate to the Raft directory:

```console
cd Raft
```

Install the necessary modules:

```console
pip install -r requirements.txt
```

The `raft.proto` file is defined in the current /Raft/ folder. All the services for leader election and log replication is defined in the `raft.proto` file


#### Building gRPC Code:

#### Java:
To build the Java code from the `.proto` file, run:


```
./gradlew clean build
```

#### Python:
To build the Python code from the `.proto` file, run:

```
python -m grpc_tools.protoc -I. --python_out=. --grpc_python_out=. raft.proto
```

##### LEADER ELECTION
- The service methods for the leader election for each node are implemented in: `leader_election.py`. The `leader_election.py` is located in:
`./`

##### LOG REPLICATION:
- The service methods for the Log Replication for each node are implemented in: `RaftLogReplicationServer.java`. The `RaftLogReplicationServer.java` is located in:
`/src/main/java/raft/`


##### CLIENT REQUEST:
- The method for client submitting a request to any node is implemented in: `RaftClient.java`. The `RaftClient.java` is located in:
`/src/main/java/raft/`


#### Dockerization:

All phases have been containerized using Docker. The corresponding Dockerfiles are:

- `Dockerfile.node`
- `Dockerfile.client`

To run all containers, a `docker-compose.yml` file is provided.

#### Running the Containers:

```console
docker-compose up --build
```

### Running the Code Without Docker-Compose:

#### Leader Election

To run the **Leader Election**, execute the following command, specifying its own port number as arguments:

```console
python leader_election.py --node_id 50051
python leader_election.py --node_id 50052
python leader_election.py --node_id 50053
python leader_election.py --node_id 50054
python leader_election.py --node_id 50055
```

In this setup, all the nodes start in the `follower` state at first. If a node does not recieve any heartbeat within `ELECTION_TIMEOUT` which is defined as 
```
ELECTION_TIMEOUT = random.uniform(1.5, 3.0)
```
Each node starts the election by transitioning to the `candidate` state. Through maximum vote, only one node is selected as the `leader`.


#### Log Replication

Then, the leader starts the Log Replication phase.
To run the  **Log Replication Phase**, execute the following command, specifying its own port number as argument.
```
./gradlew run --args="60051"
./gradlew run --args="60052"
./gradlew run --args="60053"
./gradlew run --args="60054"
./gradlew run --args="60055"
```
*It is recommended to run the LOG Replication commands before leader election, to keep all the nodes up and running for a smooth handoff of chosen leader node to the Log Replication Phase.*

#### Client Request Handling

To simulate a client submitting an operation request to any node (leader/follower),
execute the following command, specifying the requesting node's port number and operation as arguments.

```
./gradlew runClient --args="60051 myOperation"
```


*It is recommended to run the containerized version of the nodes instead of executing them locally, as the ports are properly managed within the containers.*