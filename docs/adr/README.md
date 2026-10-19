# Architecture Decision Records

An Architecture Decision Record (ADR) captures one significant design decision: the context it was taken in, what was decided, its consequences and the alternatives that were discarded. Together they explain *why* Argus looks the way it does, which the code alone cannot.

## How to write one

1. Copy [`template.md`](template.md) to `NNNN-short-title.md`, with the next free number.
2. Fill in every section. Keep it short: a page is usually enough.
3. Add it to the index below and link it from the README or the guide it affects.
4. Commit it in the pull request of the issue that takes the decision.

ADRs are **immutable once accepted**. When a decision changes, write a new ADR and mark the old one as *Superseded by* or *Amended by* the new one. Only the status line and those links are edited afterwards.

| Status | Meaning |
| :--- | :--- |
| **Proposed** | Under discussion, or waiting for a proof of concept. |
| **Accepted** | Decided. It may not be implemented yet: the [roadmap](../roadmap.md) says when it will be. |
| **Superseded** | Replaced by a later ADR. |
| **Deprecated** | No longer applies, with nothing replacing it. |

## Index

| # | Decision | Status |
| :--- | :--- | :--- |
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [0002](0002-maven-multi-module-build-on-java-25.md) | Maven multi-module build on Java 25 | Accepted |
| [0003](0003-dual-path-streaming.md) | Dual-path streaming: Aeron + SBE and Kafka + Protobuf | Accepted, amended by 0021 |
| [0004](0004-canonical-message-contracts.md) | Canonical message contracts | Accepted, amended by 0014 |
| [0005](0005-the-engine-never-blocks-on-kafka.md) | The engine never blocks on Kafka | Accepted |
| [0006](0006-archive-every-tick-and-compute-candles-on-request.md) | Archive every tick and compute candles on request | Accepted |
| [0007](0007-questdb-for-time-series-storage.md) | QuestDB for time-series storage | Accepted |
| [0008](0008-apicurio-as-schema-registry-in-every-environment.md) | Apicurio as schema registry in every environment | Accepted |
| [0009](0009-hexagonal-architecture-enforced-with-archunit.md) | Hexagonal architecture enforced with ArchUnit | Accepted |
| [0010](0010-spring-confined-to-the-edges-of-the-engine.md) | Spring confined to the edges of the engine | Accepted |
| [0011](0011-user-models-in-python-on-graalpy.md) | User models in Python, run in-process on GraalPy | Proposed |
| [0012](0012-distribute-model-state-through-compacted-topics.md) | Distribute model state through compacted Kafka topics | Accepted |
| [0013](0013-trunk-based-development-without-a-develop-branch.md) | Trunk-based development without a `develop` branch | Accepted |
| [0014](0014-one-model-engine-for-alert-rules-and-indicators.md) | One model engine for alert rules and indicators | Accepted |
| [0015](0015-built-in-models-in-java-behind-the-same-contract.md) | Built-in models in Java, behind the same contract | Accepted |
| [0016](0016-distribute-deployments-with-a-kafka-consumer-group.md) | Distribute deployments with a Kafka consumer group | Accepted |
| [0017](0017-multi-instrument-models-and-event-time.md) | Multi-instrument models, chained indicators and event time | Accepted |
| [0018](0018-output-policy-of-the-durable-path.md) | Output policy of the durable path | Accepted |
| [0019](0019-a-global-panic-button-for-models.md) | A global PANIC button for models | Accepted |
| [0020](0020-the-notification-dispatcher-only-notifies.md) | The notification dispatcher only notifies | Accepted |
| [0021](0021-run-everything-on-fargate-with-aeron-over-udp.md) | Run everything on Fargate, with Aeron over UDP | Accepted |
| [0022](0022-feed-gateway-shards-with-an-active-and-a-passive.md) | Feed gateway shards, with an active and a passive per shard | Accepted |
| [0023](0023-instrument-catalogue.md) | Instrument catalogue | Accepted |
| [0024](0024-a-feed-player-for-replay-and-backtesting.md) | A feed player for replay and backtesting | Accepted |
| [0025](0025-model-promotion-across-environments.md) | Model promotion across environments | Accepted, not implemented |
| [0026](0026-regulatory-archive-for-mifid-ii.md) | Regulatory archive for MiFID II | Accepted, not implemented |
