# 0017. Multi-instrument models, chained indicators and event time

- **Status:** Accepted
- **Date:** 2026-10-19
- **Issues:** #11, #42

## Context

Models can take data from several instruments (a spread between BTCUSDT and ETHUSDT, a US stock valued in euros) and from other indicators' outputs. Inputs arrive at different times and rates. The same models must also run on replayed data, faster than real time, for backtesting ([0024](0024-a-feed-player-for-replay-and-backtesting.md)).

## Decision

- **Instrument slots.** A model declares named slots (`leg1`, `leg2`…); a deployment fills them with instruments. A model with one slot can be deployed on many instruments, and is evaluated once per instrument.
- **As-of evaluation.** A deployment is evaluated **on every update of any of its inputs, with the latest value of every other input**. The SDK tells the model how old each input is, so it can ignore a stale leg (for example a stock outside its trading hours).
- **Chained indicators.** A deployment can consume other deployments' outputs. The dependencies form a graph; the BFF rejects a deployment that would create a cycle.
- **Event time.** Windows and the model's notion of "now" (`ctx.now`) use the events' `exchangeTimestamp`, never the system clock. Wall-clock time is only used to measure latency.

## Consequences

- The same model code runs live, in a replay at any speed, and in a backtest, with the same results.
- Slow inputs do not block fast ones; staleness is explicit instead of hidden.
- Windows must handle late or out-of-order events per input (the venue's own ordering is kept per instrument).

## Alternatives considered

- **Wait for every input before evaluating (barrier).** One slow instrument would stall the model.
- **Wall-clock windows.** Simpler, but break any replay faster than real time.
- **Fixed number of instruments per model.** Simpler configuration, but excludes spreads, baskets and FX conversion.
