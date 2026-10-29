# 0003. Dual-path streaming: Aeron + SBE and Kafka + Protobuf

- **Status:** Accepted, amended by [0021](0021-run-everything-on-fargate-with-aeron-over-udp.md) and [0027](0027-the-feed-gateway-publishes-market-data-on-both-paths.md)
- **Date:** 2026-10-14
- **Issues:** #3, #4, #5, #11

## Context

Market data must reach the engine, and model outputs must reach trading consumers, with as little latency and jitter as possible. The same data must also be archived, streamed to the UI, and trigger notifications, where durability, replay and fan-out matter more than microseconds. No single transport does both well.

## Decision

Two paths, each with its own transport and encoding:

| Path | Transport | Encoding | Used for |
| :--- | :--- | :--- | :--- |
| **Fast path** | Aeron | SBE | Market data into the engine, model outputs to low-latency consumers |
| **Durable path** | Apache Kafka (Amazon MSK on AWS) | Protobuf, registered in a schema registry | Archiving, notifications, model distribution, UI streaming |

Both carry the same values with the same conventions ([0004](0004-canonical-message-contracts.md)), so a publisher copies fields from one encoding to the other without converting them. The engine writes to Kafka asynchronously ([0005](0005-the-engine-never-blocks-on-kafka.md)).

The original decision used Aeron IPC within a host and UDP between hosts. [0021](0021-run-everything-on-fargate-with-aeron-over-udp.md) changes it to UDP everywhere.

## Consequences

- The fast path stays free of broker round trips, allocation and reflection.
- Two encodings must be kept in sync; tests check that the Protobuf field numbers match the SBE field ids.
- Aeron delivers only to live subscribers: anything that needs history reads it from Kafka or QuestDB.

## Alternatives considered

- **Kafka only.** Simpler, but a broker round trip and batching add milliseconds of latency and much more jitter.
- **Aeron only, with Aeron Archive for durability.** Possible, but Kafka is what the rest of the ecosystem speaks (connectors, MSK, schema registries), and the author already knows it.
- **Chronicle Queue.** Excellent on a single host, weaker across hosts and in containers.
