# 0024. A feed player for replay and backtesting

- **Status:** Accepted, to be built at the start of Phase 3
- **Date:** 2026-10-19
- **Issues:** #11, #43

## Context

Models must be tested on historical data before going live, benchmarks need reproducible input, and some demos need instruments that Binance does not list (a US stock, a EUR/USD future). The original design gave the feed gateway a replay mode, mixing live and recorded data in one service.

## Decision

A new service, **`argus-feed-player`**, reads recorded sessions from files and publishes them with the same SBE messages as the gateway. The gateway only handles live feeds.

- **Files:** one canonical format of our own (Parquet with the same fields as the SBE messages), plus converters from Binance's public historical data and from QuestDB exports.
- **Two modes:**
  - **Backtest:** original timestamps, configurable speed (real time, ×N, as fast as possible), and **isolated**: its deployments do not notify, are not mixed into the archive, and run on separate engine instances so they cannot affect live latency.
  - **Simulated live:** timestamps shifted to "now", so recorded instruments can be combined with live ones (for example, a recorded US stock with the live EURUSDT from Binance).
- Recorded instruments are registered in the instrument catalogue ([0023](0023-instrument-catalogue.md)) with their own venue code.
- Replays work because the engine runs on **event time** ([0017](0017-multi-instrument-models-and-event-time.md)).

## Consequences

- Backtests run the **exact code** that runs live, which is the strongest test of a model. Research in the quant lab (vectorised pandas) remains the fast, approximate path.
- Deterministic input for end-to-end tests and benchmarks.
- One more service, and a file format to maintain.

## Alternatives considered

- **Replay mode inside the feed gateway.** Mixes two responsibilities and puts replay code in the live service.
- **Replay from Kafka.** Market topics keep only a few days ([0006](0006-archive-every-tick-and-compute-candles-on-request.md)).
- **Backtesting only in the quant lab.** Does not test the code that runs in production.
