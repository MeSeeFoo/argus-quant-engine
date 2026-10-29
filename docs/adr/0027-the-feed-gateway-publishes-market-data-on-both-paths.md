# 0027. The feed gateway publishes market data on both paths

- **Status:** Accepted
- **Date:** 2026-10-29
- **Issues:** #97
- **Amends:** [0003](0003-dual-path-streaming.md), [0005](0005-the-engine-never-blocks-on-kafka.md), [0006](0006-archive-every-tick-and-compute-candles-on-request.md)

## Context

[0003](0003-dual-path-streaming.md) and [0005](0005-the-engine-never-blocks-on-kafka.md) have the model engine publish every market event it receives to Kafka (`argus.market.trades.v1`, `argus.market.quotes.v1`), next to its own outputs. The archiver and the BFF read market data from those topics ([0006](0006-archive-every-tick-and-compute-candles-on-request.md)).

That makes the engine a relay for data it does not produce, which brings three problems:

- **Duplicates.** The engine runs as 1..n instances that share the deployments ([0016](0016-distribute-deployments-with-a-kafka-consumer-group.md)). If every instance republished what it receives, each tick would reach Kafka once per instance, and choosing one instance to publish would be coordination the engine does not otherwise need.
- **Coupling.** When the engine stops (a restart, a crash, a deployment), the archive stops too, although the gateway is still receiving the market.
- **Responsibility.** Republishing ticks adds nothing to the engine's job, which is to run models.

The feed gateway already talks to Kafka: its active and passive are elected through a lease topic ([0022](0022-feed-gateway-shards-with-an-active-and-a-passive.md)).

The two kinds of market data are not alike. A trade is an execution: each one counts, and candles need all of them for their volume, high and low. A top-of-book update replaces the previous one: on a liquid instrument it changes many times per millisecond, and the archive and the UI only need its recent state, as with indicator values ([0018](0018-output-policy-of-the-durable-path.md)).

## Decision

- The **feed gateway publishes market data on both paths**: Aeron for the fast path and Kafka (`argus.market.trades.v1`, `argus.market.quotes.v1`) for the durable path, with the same values ([0004](0004-canonical-message-contracts.md)).
  - **Aeron** carries every trade and every top-of-book update, as before.
  - **`argus.market.trades.v1`** carries every trade, never conflated, so candles stay exact ([0006](0006-archive-every-tick-and-compute-candles-on-request.md)).
  - **`argus.market.quotes.v1`** is **conflated**: for each instrument, the latest top-of-book is published at most once per interval, and only if it changed since the last one published. The interval is a configuration property of the gateway. This amends [0006](0006-archive-every-tick-and-compute-candles-on-request.md), which archived every top-of-book update.
  - The conflation runs in the publisher agent, off the fast path, in pre-allocated state (one slot per instrument), as for indicator values ([0018](0018-output-policy-of-the-durable-path.md)).
- The **model engine publishes only its outputs** to Kafka (`argus.indicators.v1`, `argus.alerts.v1`). It no longer writes to the market topics.
- The rule of [0005](0005-the-engine-never-blocks-on-kafka.md) applies to the gateway as it does to the engine: the gateway's fast-path agent never calls Kafka. It copies each event into a pre-allocated Agrona ring buffer, and a separate publisher agent drains it into Kafka. When the buffer is full, the event is dropped for Kafka only, counted in a metric, and still published on Aeron.
- **Only the active of a shard publishes**, on both paths. When the passive takes over, it publishes the events newer than the last ones the active published to both paths too ([0022](0022-feed-gateway-shards-with-an-active-and-a-passive.md)).
- **Consumers of the market topics tolerate duplicates.** A trade is identified by venue, symbol and `tradeId`; the archiver deduplicates on that key (QuestDB's `DEDUP UPSERT KEYS`), as the engine already does on the fast path.
- The **feed player** follows the same rule in **simulated live**, where its instruments are archived and shown like live ones. In **backtest** it does not publish to the market topics, so recorded sessions never mix with the live archive ([0024](0024-a-feed-player-for-replay-and-backtesting.md)).

## Consequences

- Each tick reaches Kafka once, from the service that receives it, however many engine instances run.
- The archive and the UI keep receiving market data while the engine is down or under PANIC ([0019](0019-a-global-panic-button-for-models.md)).
- The engine gets simpler: one Kafka publisher, for its outputs only.
- The gateway gets a second output adapter (Kafka with Protobuf), a second agent and its ring buffer. Kafka being slow or down must never delay the Aeron publication; tests have to prove it.
- The active's ring buffer is lost if it crashes, so a failover can leave a gap or repeat events on Kafka. Gaps show in `tradeId` and `sequence`, repeats are removed by the archiver; nothing is recovered from the venue, as in [0005](0005-the-engine-never-blocks-on-kafka.md).
- The model engine's Kafka volume no longer grows with the market feed, only with its outputs, which are already bounded ([0018](0018-output-policy-of-the-durable-path.md)).
- The volume of `argus.market.quotes.v1` is bounded by the number of instruments and the interval, not by the activity of the market. Its consumers rarely see a full buffer, and only trades are at risk of being dropped under pressure.
- The archive no longer holds every top-of-book update: spreads and quotes between two published ones cannot be recomputed from QuestDB. Analysis that needs them works from recorded sessions ([0024](0024-a-feed-player-for-replay-and-backtesting.md)). A shorter interval brings the archive closer to the full stream, at the cost of volume.

## Alternatives considered

- **Keep republishing from the engine.** No new code in the gateway, but it has the three problems above.
- **A separate bridge service** that subscribes to the gateway on Aeron and publishes to Kafka. Keeps Kafka producers out of the gateway, but adds a service to deploy and monitor, needs its own high availability to avoid duplicates and losses, and the gateway already depends on Kafka for its lease.
- **Conflate trades too.** Bounds the whole durable path, but candles would lose volume, highs and lows, and the archive could no longer explain a model's output.
- **Every top-of-book update to Kafka.** Exact, but its volume follows the market and dominates everything else on the durable path, for data that each update makes obsolete.
- **The archiver subscribes to Aeron directly.** Removes a hop for the archive, but the BFF and any future consumer would still need the market data on Kafka, and the archiver would lose Kafka's buffering and replay.
