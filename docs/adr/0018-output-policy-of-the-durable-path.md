# 0018. Output policy of the durable path

- **Status:** Accepted
- **Date:** 2026-10-19
- **Issues:** #11, #42

## Context

Every model output goes to the fast path. An indicator evaluated on every tick, for every instrument, sent in full to Kafka, would multiply the volume of the market feed. The UI does not need more than a few updates per second, and the archive needs to be able to explain what happened, not to store every intermediate value. This is an engineering decision, not something users configure.

## Decision

- **Fast path (Aeron): every output, always.**
- **Durable path (Kafka):**
  - **Alerts: every one.** They are few, and they are decisions that must be auditable.
  - **Indicator values: conflated.** For each deployment and instrument, the latest value is published at most once per interval (1 s by default, an engine setting) and only if it changed.
- Conflation happens in the publisher agent, off the hot path, in pre-allocated state.

## Consequences

- Kafka, the archive and the UI receive a bounded volume, independent of the tick rate.
- Not every indicator value is stored. **Any value can be recomputed**: ticks are archived complete ([0006](0006-archive-every-tick-and-compute-candles-on-request.md)), models are deterministic and run on event time ([0017](0017-multi-instrument-models-and-event-time.md)), and outputs carry the deployment revision ([0014](0014-one-model-engine-for-alert-rules-and-indicators.md)).
- A consumer that needs every value must subscribe to the fast path.

## Alternatives considered

- **Every value to Kafka.** Volume grows with ticks × deployments.
- **Policy chosen per deployment by the user.** Users would choose "everything" by default; volume is an operational concern.
- **Sampling without "only if changed".** Publishes identical values for quiet instruments.
