# 0022. Feed gateway shards, with an active and a passive per shard

- **Status:** Accepted, to be built after Phase 3
- **Date:** 2026-10-19
- **Issues:** #11, #43

## Context

The feed gateway must support high availability from the start and scale to 1..n instances. A WebSocket market feed is live: a standby cannot "resume" the active's stream, it can only have received the same stream itself. Shared memory between instances is not available on Fargate ([0021](0021-run-everything-on-fargate-with-aeron-over-udp.md)). Duplicating every message on the fast path is not wanted.

## Decision

### Shards and pairs

- Instruments are grouped into **shards** ([0023](0023-instrument-catalogue.md) says which instrument belongs to which shard).
- **Each shard is an ECS service with two tasks**, spread across availability zones. Both tasks read their shard id from the service configuration. Scaling means adding shards, not tasks.

### Who is active and who is passive

- Both tasks join a **Kafka consumer group for their shard**, subscribed to a **single-partition lease topic** (`argus.feed-lease.shard-<n>.v1`). Kafka gives the partition to one of them: that one is the **active**. The other one gets nothing: it is the **passive**, and it knows it.
- If the active dies, Kafka detects it (`session.timeout.ms`, a few seconds) and gives the partition to the passive, which becomes active. The lease topic carries no messages: it only serves to elect an owner, as Kafka Connect and Schema Registry do for their leaders.

### Resuming where the active stopped

- The passive keeps **its own connection** to the venue and a **ring buffer** of the last 30 s of the feed.
- It **subscribes to the active's Aeron output**, so it always knows the last `tradeId` (and `bookUpdateId`) the active published.
- When it takes over, it publishes from its buffer every event newer than that, then continues live. Top-of-book needs no catch-up: the latest update is enough.
- The buffer must be longer than failure detection plus the rebalance; 30 s leaves a wide margin.

### Safety net

The engine **drops any trade whose `tradeId` is not newer than the last one seen** for that instrument (one `long` per instrument, no allocation). It covers the moment when an isolated former active and the new one both publish, and moves of instruments between shards ([0023](0023-instrument-catalogue.md)). Binance trade ids are consecutive per symbol, so the engine also detects gaps from them. The gateway's `sequence` only detects losses within one publisher session.

### Status

Each gateway publishes the state of its connections (venue, shard, role, connected, last message time) to the compacted topic `argus.feed-status.v1`. The BFF reads it to show the status of every gateway; Actuator health checks remain for ECS.

## Consequences

- No coordination code of our own: Kafka elects, ECS restarts failed tasks.
- No message loss on failover if the passive's own connection was healthy, at the cost of a latency spike of a few seconds while Kafka detects the failure.
- Two connections per shard to the venue.
- Not elastic: adding capacity means adding a shard in Terraform and reassigning instruments in the catalogue.

## Alternatives considered

- **Custom partition assignor** (one group for all instances, the leader assigns an active and a passive per shard, as Kafka Streams does with standby replicas). Elastic, but delicate code, and it needs the "classic" consumer group protocol. Kept as the evolution path if there are ever hundreds of instruments.
- **Rendezvous hashing over the list of members.** No coordinator, but members can briefly disagree and the membership detection has to be built.
- **Leases in DynamoDB.** A common AWS pattern, but one more component and another setup for local development.
- **ZooKeeper or etcd** (ordered ephemeral nodes: the second in line is the passive). Exactly the right semantics, but brings back ZooKeeper just as Kafka got rid of it.
- **Hot-hot: both publish, the engine deduplicates.** No failover at all, but doubles fast-path traffic.
- **Aeron Cluster (Raft).** Strong consistency, but three nodes with stable addresses, hard on Fargate and too much for this use.
- **Gap recovery through the venue's REST API.** Rejected by the author; the passive's buffer avoids depending on the venue's history endpoints.
- **Shared memory between active and passive.** Not available across Fargate tasks.
