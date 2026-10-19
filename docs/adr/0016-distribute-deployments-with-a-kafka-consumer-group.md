# 0016. Distribute deployments with a Kafka consumer group

- **Status:** Accepted, to be built after Phase 3
- **Date:** 2026-10-19
- **Issues:** #11, #42

## Context

Deployments must be spread across 1..n instances of `argus-model-engine`, new deployments must start without a restart, and the deployments of an instance that fails must move to the others. Writing membership, assignment and failover by hand is error-prone.

## Decision

- `argus.model-deployments.v1` is a compacted topic with N partitions, **keyed by deployment id**. N is the maximum parallelism.
- Engine instances form **one consumer group** on it. Kafka assigns partitions; each instance runs the deployments of the partitions it owns. Kafka provides membership, assignment, failover and rebalancing.
- When an instance receives a partition, it rebuilds the state of its deployments: it fills their windows from QuestDB (or from a replay) and warms them up before emitting.
- Deployments are not co-located with their dependencies. A deployment that consumes another one's output subscribes to it over Aeron UDP ([0021](0021-run-everything-on-fargate-with-aeron-over-udp.md)), wherever it runs.

## Consequences

- No coordination code of our own; scaling out is starting another task.
- A rebalance pauses the moved deployments while they warm up again. Cooperative rebalancing keeps the others running.
- The consumer group's membership is also how the BFF knows which instances must acknowledge a PANIC ([0019](0019-a-global-panic-button-for-models.md)).

## Alternatives considered

- **Static assignment in configuration.** Not elastic, and failover is manual.
- **Partition by instrument.** Good locality, but models with several instruments ([0017](0017-multi-instrument-models-and-event-time.md)) do not have one instrument to partition by.
- **A coordinator service or leases in a database.** More code and one more component to make highly available.
