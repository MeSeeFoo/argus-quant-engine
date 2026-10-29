# 0006. Archive every tick and compute candles on request

- **Status:** Accepted, amended by [0027](0027-the-feed-gateway-publishes-market-data-on-both-paths.md)
- **Date:** 2026-10-14
- **Issues:** #3, #7, #11

## Context

Charts need candles at many resolutions (1 s, 1 min, 1 h…), models need their own windows, and research needs the raw data. Pre-aggregating candles fixes the resolutions in advance and loses information.

## Decision

- The market topics on Kafka (`argus.market.trades.v1`, `argus.market.quotes.v1`) carry **every trade and every top-of-book update**, never aggregated.
- The archiver stores every tick in QuestDB ([0007](0007-questdb-for-time-series-storage.md)).
- Candles are computed **on request** by the BFF with QuestDB's `SAMPLE BY`, at the resolution the user picks.
- Models keep their own windows in memory, sized by what each model declares.
- Kafka is **not** the archive: market topics keep a few days, QuestDB holds the queryable history.

## Consequences

- Any resolution can be derived later without reprocessing.
- More storage and write throughput than storing candles; acceptable at the volume of a handful of Binance symbols.
- Any value a model computed can be recomputed from the archived ticks ([0018](0018-output-policy-of-the-durable-path.md) relies on this).

## Alternatives considered

- **Pre-aggregated candles in the engine.** Fixed resolutions, and a second source of truth.
- **Kafka with long retention as the archive.** Not queryable by time ranges, and expensive on MSK.
