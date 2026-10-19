# 0007. QuestDB for time-series storage

- **Status:** Accepted
- **Date:** 2026-10-14
- **Issues:** #3, #6, #11

## Context

Every tick is archived ([0006](0006-archive-every-tick-and-compute-candles-on-request.md)), and the BFF and the quant lab query it by time range and resolution. The store must ingest a high rate of small rows and answer time-bucketed queries fast.

## Decision

- **QuestDB** for market data, model outputs and alerts, in time-partitioned tables. The archiver writes with the InfluxDB Line Protocol (ILP) in batches; readers use SQL over the PostgreSQL wire protocol (`SAMPLE BY`, `LATEST ON`, `ASOF JOIN`).
- **PostgreSQL** (Amazon RDS on AWS) for relational data: models, deployments, the instrument catalogue, users' notification settings.
- On AWS, QuestDB is self-managed on EC2 with EBS, since there is no managed QuestDB on AWS.

## Consequences

- Time-series queries are simple and fast; candles are one SQL statement.
- Two databases to operate instead of one.
- QuestDB on AWS is the only stateful service the project runs itself; it needs its own backup plan.

## Alternatives considered

- **TimescaleDB.** One database for everything, but slower ingestion and heavier queries for this use case. Dropped on 2026-10-14.
- **InfluxDB.** Good ingestion, weaker SQL and joins.
- **Amazon Timestream.** Managed, but AWS-only, which breaks the "same stack locally and on AWS" principle.
- **kdb+.** The industry reference for tick data, but commercial and with a niche language.
