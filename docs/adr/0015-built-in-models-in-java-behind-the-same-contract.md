# 0015. Built-in models in Java, behind the same contract

- **Status:** Accepted
- **Date:** 2026-10-19
- **Issues:** #11, #42

## Context

Users write models in Python ([0011](0011-user-models-in-python-on-graalpy.md)). Many indicators are standard (SMA, EMA, VWAP, mid price, RSI, ATR, Bollinger bands) and are used by many deployments on every tick. Running all of them in the sandbox would waste the zero-allocation Java path the engine is built around.

## Decision

- Standard indicators are implemented in Java in a separate Maven module, **`libs/builtin-models`**, without allocation in steady state.
- Java and Python models implement **the same internal port**: same `ModelConfiguration`, same outputs. For the engine, the BFF and the UI they are interchangeable; the UI shows them in one catalogue.
- Python models consume built-in indicators through read-only views, so the expensive work stays in Java.

## Consequences

- Common indicators are fast and cheap, and can be benchmarked with JMH on their own.
- A Java implementation of a Python model (or the other way round) can be swapped without changing deployments or consumers.
- New built-in models need a release of the engine; user models do not.

## Alternatives considered

- **Everything in Python.** Simpler, but every VWAP would run in the sandbox on every tick.
- **Built-in models inside the engine module.** Harder to test and benchmark in isolation.
