# 0014. One model engine for alert rules and indicators

- **Status:** Accepted
- **Date:** 2026-10-19
- **Issues:** #11, #42, #45
- **Amends:** [0004](0004-canonical-message-contracts.md), [0012](0012-distribute-model-state-through-compacted-topics.md)

## Context

The original design had a signal engine with built-in indicators, a separate rule runtime library for Python rules, and a single output: a `Signal` (long / short / flat). In the architecture review, the purpose of the engine was restated: run **scripts that users write in the UI**, loaded at runtime, fed with market data or with the output of other indicators (synthetic market data). Two kinds of script were identified, and the `Signal` did not fit either.

## Decision

### One service

`argus-signal-engine` and `argus-rule-runtime` become one service, **`argus-model-engine`**, built from three Maven modules:

| Module | Content |
| :--- | :--- |
| `services/model-engine` | The service: agents, windows, adapters, distribution of deployments |
| `libs/model-runtime` | The GraalPy sandbox and the `argus` Python SDK ([0011](0011-user-models-in-python-on-graalpy.md)) |
| `libs/builtin-models` | Models implemented in Java ([0015](0015-built-in-models-in-java-behind-the-same-contract.md)) |

### Vocabulary

| Concept | What it is | Example |
| :--- | :--- | :--- |
| **Model** | Versioned code plus its `ModelConfiguration`. Two kinds: `AlertRule` and `Indicator` | "VWAP v3" |
| **Deployment** | A model version bound to instruments and parameters, running | "VWAP v3 on BTCUSDT and ETHUSDT" |
| **Output** | What a deployment emits: an `Alert` or an `IndicatorValue` | "VWAP = 64 210.5" |

- **`Indicator`**: given market data (raw ticks or windows) or other indicators' values, computes one or more named values. Example: the mid price, a VWAP, Bollinger bands.
- **`AlertRule`**: given the same kind of inputs, decides whether to raise an alert, with a severity and a message.

### `ModelConfiguration`

Each model returns a `ModelConfiguration`, built programmatically with a builder (PEP 8 names in Python): the windows it needs (by time or by number of ticks, sliding or tumbling), its **named instrument slots** (left open, filled when deploying), the indicators it consumes, its named outputs, and its parameters with their allowed ranges. The configuration is evaluated **once, when the model is registered**, in the sandbox, and stored as data (JSON): the engine and the UI read it without running user code.

### Outputs

Every output goes to the fast path (Aeron) and, following [0018](0018-output-policy-of-the-durable-path.md), to the durable path (Kafka). The `Signal` message is removed from the contracts and replaced by:

- **`IndicatorValue`**: deployment id and revision, the instruments it refers to, and its values as `double`, in the order declared in `ModelConfiguration`. Computed values use `double`; market data keeps `Decimal64`.
- **`Alert`**: deployment id and revision, the instruments, a severity and a message.

### Model state topics

`argus.rules.v1` and `argus.rule-params.v1` become `argus.models.v1` (definitions) and `argus.model-deployments.v1` (deployments, including their parameters). Parameters calibrated in the quant lab update a deployment through the BFF, so they are validated against the declared ranges and versioned.

## Consequences

- One deployable for everything that evaluates models; the sandbox and the SDK can still be tested on their own.
- Models are reusable templates: one VWAP serves any instrument.
- Each change to a deployment bumps its revision, and outputs carry it: any output can be traced to the exact code and parameters that produced it.
- The contracts change (`Signal` removed): no consumer existed yet.

## Alternatives considered

- **Keep the signal engine and the rule runtime as separate services.** A network hop between data and rules, for no benefit.
- **Keep `Signal` (long / short / flat) as the only output.** It does not describe indicators, and alerts need a severity and a message.
- **Parameters published directly by the quant lab to Kafka.** Skips validation and versioning.
