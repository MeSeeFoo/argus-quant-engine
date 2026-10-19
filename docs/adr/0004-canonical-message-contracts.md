# 0004. Canonical message contracts

- **Status:** Accepted, amended by [0014](0014-one-model-engine-for-alert-rules-and-indicators.md)
- **Date:** 2026-10-15
- **Issues:** #4, #5, #11

## Context

Every service exchanges market data and model outputs. If each one used the exchange's formats, a change at Binance would ripple through the whole system, and adding a venue would mean new message types everywhere. Prices need exact decimal arithmetic, and the fast path must decode without allocating.

## Decision

`argus-contracts` is the single source of truth for every message, with **our own canonical contracts**:

- **Decimals** as an `int64` mantissa with a constant exponent of −8 (`Decimal64`). It covers every Binance tick size; the exponent takes no bytes on the wire.
- **Timestamps** in nanoseconds since the Unix epoch, UTC. Both the venue time and the time Argus received the event are carried, to measure latency along the path.
- **Venue and symbol as text** (`BINANCE`, `BTCUSDT`), fixed-length in SBE (`char[12]`, `char[24]`), so users can name instruments directly in models without an id catalogue, and messages keep a fixed size.
- **A sequence number per publisher**, so that gaps are detectable.
- Exchange-specific codecs live only in the feed gateway, which maps them to these messages.
- Code is generated at build time and never committed. Tests pin the wire format (schema id, template ids, block lengths, Protobuf field numbers).

The original set of outputs was a `Signal` (long / short / flat). [0014](0014-one-model-engine-for-alert-rules-and-indicators.md) replaces it with `IndicatorValue` and `Alert`.

## Consequences

- Venue changes stay inside the gateway.
- Fixed-point decimals avoid floating-point errors on prices; computed values that are not prices may use `double` ([0014](0014-one-model-engine-for-alert-rules-and-indicators.md)).
- Fixed-length strings reject longer values instead of truncating them.

## Alternatives considered

- **Reuse Binance's SBE schemas.** Couples every service to one venue.
- **`double` for prices.** Rounding errors in sums and comparisons.
- **`google.protobuf.Timestamp` and decimal wrapper messages.** They need imports, cost bytes, and break the 1:1 mapping with SBE.
- **Numeric instrument ids.** Smaller, but every consumer would need a catalogue to read the data.
