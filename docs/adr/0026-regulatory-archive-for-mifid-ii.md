# 0026. Regulatory archive for MiFID II

- **Status:** Accepted, documented only (not implemented)
- **Date:** 2026-10-19
- **Issues:** #11, #44

## Context

A firm that trades algorithmically in the EU must keep records and run controls under MiFID II and its technical standards. Argus does not execute orders ([README non-goals](../../README.md#non-goals)), but it reproduces the kind of signal-generation platform those rules apply to. Showing where each requirement would land is part of the project's purpose. QuestDB is a queryable store, not an immutable archive, and Kafka keeps only days.

## Decision

This describes the design; it is not implemented.

| Requirement | Where it lands in Argus |
| :--- | :--- |
| **Record keeping** (MiFID II art. 16(6) and 17(2): at least 5 years, up to 7 at the regulator's request) | Kafka topics exported to **Amazon S3** with **Object Lock** and a lifecycle to colder storage classes |
| **Clock synchronisation** (RTS 25: UTC traceability; 1 µs granularity for high-frequency trading, 1 ms for other algorithmic trading) | Nanosecond timestamps in every message ([0004](0004-canonical-message-contracts.md)). On Fargate, Amazon Time Sync over NTP gives millisecond-level accuracy, so Argus targets the non-HFT level; microsecond accuracy would need PTP on supported EC2 instances |
| **Kill functionality** (RTS 6, art. 12) | The PANIC button ([0019](0019-a-global-panic-button-for-models.md)) |
| **Testing and change control of algorithms** (RTS 6) | Model promotion ([0025](0025-model-promotion-across-environments.md)) and backtests with the feed player ([0024](0024-a-feed-player-for-replay-and-backtesting.md)) |
| **Real-time monitoring** (RTS 6) | Observability and alerting (Phase 8) |
| **Audit of who did what** | `argus.audit.v1`: creation, deployment, parameter changes, PANIC and re-arm, each with its user |

- **Export:** Kafka Connect (MSK Connect on AWS, with an open-source S3 sink connector) writes the records **as they are, with their schema id**, so the archived bytes are the evidence. A Parquet copy can be derived for queries (Athena).
- **Topics archived:** market data, alerts, the conflated indicator values ([0018](0018-output-policy-of-the-durable-path.md)), notification deliveries, the audit log, and the model and deployment topics.

## Consequences

- **Object Lock in compliance mode cannot be undone**, not even by the root account, until retention expires. It conflicts with the project's `terraform destroy` after each session: the bucket could not be deleted and would keep costing money. A demo would use governance mode with a short retention; production would use compliance mode with 5 years.
- Out of scope, because Argus does not handle orders: order record keeping (RTS 24) and transaction reporting (RTS 22).
- The data lake mentioned in the README comes for free once this export exists.

## Alternatives considered

- **QuestDB as the regulatory archive.** Mutable, and not designed for multi-year retention.
- **Long retention on Kafka.** Expensive on MSK, and not immutable.
- **A custom S3 writer in the data archiver.** Possible, but a regulatory archive should be boring and standard.
