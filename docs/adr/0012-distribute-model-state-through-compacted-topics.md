# 0012. Distribute model state through compacted Kafka topics

- **Status:** Accepted, amended by [0014](0014-one-model-engine-for-alert-rules-and-indicators.md) and [0016](0016-distribute-deployments-with-a-kafka-consumer-group.md)
- **Date:** 2026-10-15
- **Issues:** #7, #11

## Context

Models are created and changed in the BFF, and calibrated in the quant lab, while the engine runs. Every engine instance must learn about changes without a restart, and rebuild its full state after one. The data must also be validated and versioned somewhere durable.

## Decision

- **PostgreSQL is the system of record**: the BFF validates and versions every change there.
- After storing it, the BFF publishes the **current state** to a **compacted** Kafka topic, keyed by the entity id. Kafka keeps the latest record per key; a record with a null value (tombstone) deletes the entity.
- Engine instances consume these topics and swap in immutable state **outside the hot path**. On restart, they read the topics from the beginning and rebuild their state.

The original topics were `argus.rules.v1` and `argus.rule-params.v1`. After the review they are `argus.models.v1` and `argus.model-deployments.v1` ([0014](0014-one-model-engine-for-alert-rules-and-indicators.md)), and the same pattern carries the PANIC state ([0019](0019-a-global-panic-button-for-models.md)) and the instrument catalogue ([0023](0023-instrument-catalogue.md)).

## Consequences

- Services never call the BFF or the database to load their configuration; startup only needs Kafka.
- Two copies of the state (PostgreSQL and the topic) must stay consistent: the BFF writes to the database first and publishes afterwards. A failed publish is retried; a periodic reconciliation can republish everything.
- Compacted topics are not a history: the history lives in PostgreSQL.

## Alternatives considered

- **Services read PostgreSQL directly.** Couples every service to the schema and the database's availability.
- **REST calls to the BFF at startup and polling for changes.** Slower to propagate and puts load on the BFF.
- **Kafka as the system of record.** Validation, versioning and queries are much easier in a database.
