# 0011. User models in Python, run in-process on GraalPy

- **Status:** Proposed, pending the GraalPy proof of concept at the start of Phase 3
- **Date:** 2026-10-14 (reviewed 2026-10-19)
- **Issues:** #3, #11, #42

## Context

Users (quants, data scientists) write their own models from the UI, and the engine loads them at runtime ([0014](0014-one-model-engine-for-alert-rules-and-indicators.md)). The people who write them work in Python. The code is **untrusted**: it must not reach the network, the file system or the host, and a slow or broken model must not stall the engine.

During the architecture review (2026-10-19) we agreed that Argus is a low-latency system, not an ultra-low-latency one: milliseconds end to end are fine, and microseconds inside the engine are a goal, not a requirement. That weakens the "no network hop" argument for running models in-process, so the choice was reconsidered.

## Decision

- Models are written in **Python**, with a small `argus` SDK (`ModelConfiguration`, read-only views of the built-in indicators, `argus.math`). The SDK follows Python conventions (PEP 8: `add_window(ms=…)`).
- They run **in-process** through the GraalVM Polyglot API with **GraalPy**, in a sandbox configured with `SandboxPolicy`: no host access beyond the SDK, no I/O, no threads, no native code, and per-evaluation limits on statements, CPU time and heap.
- The reason to keep GraalPy after the review is **security, not latency**: GraalPy offers a sandbox inside the process, while CPython does not.
- Models are warmed up on recent data before going live, run on their own evaluator agent, and are disabled by a circuit breaker if they fail repeatedly.
- pandas and NumPy are C extensions and cannot run under this sandbox. They live only in `argus-quant-lab`; `argus.math` exposes vetted numerical functions implemented in Java.

The proof of concept must confirm which policy GraalPy supports (`ISOLATED` or `UNTRUSTED`, which need the `python-isolate` artifact) and measure the cost of a call from the engine. If it fails, this ADR is superseded.

## Consequences

- Users write plain Python instead of learning a rule language.
- No pandas or NumPy in live models; heavy computation belongs in the quant lab, which publishes calibrated parameters.
- GraalPy needs warm-up before its JIT kicks in; the warm-up uses replayed data.
- The engine image runs on Oracle GraalVM 25.

## Alternatives considered

- **A JSON rule DSL.** Rejected by the author: it reinvents a programming language, badly.
- **CPython workers out of process (with NumPy).** Users would get their usual libraries, but CPython has no in-process sandbox: each worker would need its own container for isolation, which makes dynamic loading and distribution across instances much harder, and adds a hop to every evaluation.
- **Jython.** Python 2 only and unmaintained.
- **Java-only rules.** Fast, but not what the users write.
