# 0005. The engine never blocks on Kafka

- **Status:** Accepted, amended by [0027](0027-the-feed-gateway-publishes-market-data-on-both-paths.md)
- **Date:** 2026-10-14
- **Issues:** #3, #11

## Context

The engine publishes every market event and every model output to Kafka ([0003](0003-dual-path-streaming.md)). A Kafka producer can block when its buffer is full, for example when a broker is slow or unreachable. If that happened on the fast path, a Kafka problem would turn into latency for trading consumers.

## Decision

The fast path never calls Kafka. It writes to a pre-allocated in-memory ring buffer (Agrona), and a separate publisher agent drains it into Kafka.

If the ring buffer is full, **messages are dropped** instead of slowing the fast path down. Every drop is counted in a metric, and the per-publisher sequence leaves a gap that consumers can detect.

## Consequences

- Slow brokers or consumers cannot affect fast-path latency.
- The durable path may lose messages under pressure. This is accepted on purpose: it is visible (metric and sequence gaps), and market data can be replayed from the venue or recomputed.
- The ring buffer must be sized for expected bursts; its fill level is a metric worth alerting on.

## Alternatives considered

- **Synchronous or blocking producer.** Couples fast-path latency to broker health.
- **Unbounded in-memory queue.** Never drops, but runs out of memory during a long outage instead.
- **Spill to local disk.** Adds I/O and recovery logic for little benefit, since QuestDB can be refilled from the venue's historical data.
