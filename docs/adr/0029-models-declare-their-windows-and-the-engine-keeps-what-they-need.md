# 0029. Models declare their windows, and the engine keeps what they need

- **Status:** Accepted
- **Date:** 2026-11-10
- **Issues:** #111, #112
- **Amends:** [0017](0017-multi-instrument-models-and-event-time.md)

## Context

A model declares its own windows ([0014](0014-one-model-engine-for-alert-rules-and-indicators.md)). One model wants the top of book of instrument A in slices of 100 updates; another wants every trade and every top-of-book update of instrument B. The engine therefore cannot summarise events into fixed bars when they arrive: what a bar throws away, a model may need.

What each model needs is known before the first event arrives, from its configuration. Models receive market data as objects, never as a ring buffer or bytes, and several models may read the same events. The engine must allocate nothing in steady state, and a slow model must not stop the engine from reading the feed.

## Decision

### Two agents joined by a Disruptor

```text
reader agent                     LMAX Disruptor            evaluator agent
Aeron poll ─▶ decode ─▶ checks ─▶ ring of reused slots ─▶ stores ─▶ windows ─▶ models
```

- The **reader agent** polls Aeron, decodes with flyweights, drops old trades and counts `sequence` gaps ([0022](0022-feed-gateway-shards-with-an-active-and-a-passive.md)). It publishes trades and top-of-book updates into **one** Disruptor ring, so the evaluator sees them in arrival order across instruments.
- The **evaluator agent** drains the ring through an `EventPoller`, which keeps it an Agrona agent that Spring starts like any other ([0010](0010-spring-confined-to-the-edges-of-the-engine.md)). The stores, the windows and the models are used only on this thread, so they need no locks or memory barriers.
- **The reader never waits for the evaluator.** Each poll asks Aeron for no more messages than the ring has free slots. What was not read stays in Aeron's buffers; if the evaluator falls behind for longer than those can hold, the engine loses data on its own side (`fc=max`) and sees it as a `sequence` gap.
- The ring is only a hand-off. A slot is free again as soon as the evaluator has passed it, so nothing keeps a reference to one: the evaluator copies each event into a store.

### Set up from the models' configuration

At startup a planner reads the configuration of every deployment: which instruments and kinds of event (trades, top of book) it uses, and which windows. From that it builds one store per instrument and kind, sized to the largest need of its windows:

| Window | Store needs |
| :--- | :--- |
| Sliding, last N events | N events |
| Tumbling, slices of N events | 2N − 1 events: the last closed slice stays readable while the next one fills |

| Example | Store | Size |
| :--- | :--- | :--- |
| Model Z: tumbling window of 100 on A's top of book | A, top of book | 199 |
| Model Y: every update of B | B, trades and B, top of book | 1 each |
| Nobody uses A's trades | A, trades | 1, the minimum, so no code needs a special case |

- **Sizes are fixed at startup.** A store never grows, and never overwrites an event that a window still needs. Steady state allocates nothing.
- **Objects are read-only to models, not immutable.** Each stored object is a mutable class that implements a read-only interface (`Trade`, `BestBidAsk`), and models only ever receive the interface. It is one object with two faces, so reading it costs no extra allocation or copy. The evaluator overwrites the object when it is reused, so a reference is valid only during the evaluation it was received in. A model that needs a value later copies the value.

### Windows by number of events

- A window copies no events: it is a range of positions over one store, so any number of models share the same objects.
- **Sliding:** the last N events. **Tumbling:** slices of N events that do not overlap; a model reads the last closed one.
- Windows by time are left for when a model needs them; they will need their own sizing rule, since how many events fit in a minute depends on the market.
- Events stay in arrival order and are never reordered, which keeps a replay identical to the original run. A window's "now" is the latest `exchangeTimestamp` of its input and never goes backwards.

### When a model is evaluated

This amends [0017](0017-multi-instrument-models-and-event-time.md), which evaluates a deployment on every update of any of its inputs. **A deployment is evaluated when one of its windows fires:** a sliding window on every event, a tumbling window when a slice closes. Evaluating on every update is a sliding window of one event. The deployment's other inputs are read as they are at that moment, as 0017 describes.

### Models run on the evaluator thread

- The evaluator runs a model as soon as one of its windows fires, then carries on with the next event. It calls it through an interface (`ModelRunner`), so how models run can change without touching the rest.
- In Phase 2 the only models are the built-in ones ([0015](0015-built-in-models-in-java-behind-the-same-contract.md)): our own code, which takes microseconds. A model that hangs would block the evaluator; that is accepted for them.
- **Planned for Python models (Phase 3), to be decided with its own ADR:** an agent per model, or per group of models, fed by its own Disruptor of pre-allocated evaluation requests, each with a copy of the window. The model's agent is the only consumer, so evaluations keep their order, and how far it lags shows when it cannot keep up: that is where a circuit breaker fits. Outputs go back through a ring to a single publisher, which keeps `sequence` monotonic. Timeouts, cancellation and memory limits come from the GraalPy sandbox ([0011](0011-user-models-in-python-on-graalpy.md)): a Java thread cannot be killed safely, but a polyglot context can be interrupted or closed.

## Consequences

- Memory is exactly what the deployments need, and known at startup.
- Models can compute anything from raw events, including a mid price on every update.
- A new deployment changes what the engine keeps. In Phase 2 deployments come from configuration, so it means a restart; distributing them at runtime (Phase 3) will need stores that can be resized.
- Two threads plus the driver's, none pinned on Fargate ([0021](0021-run-everything-on-fargate-with-aeron-over-udp.md)).
- A slow model delays the other models, as they share the evaluator thread, but not the reading of the feed until the ring is full.
- The Disruptor is a second ring buffer library next to Agrona's, used only here. Part of the reason is a learning goal, stated as such, like Spring in [0010](0010-spring-confined-to-the-edges-of-the-engine.md).
- The gateway keeps Agrona's ring, for the reasons in `MarketDataQueue`: it has several producers, and its events never leave the service as objects.

## Alternatives considered

- **Fixed 1-second and 1-minute bars.** Small and bounded memory, but a model could not build its own window from them.
- **Stores that grow between a configured initial and maximum size.** Works without knowing the models, but the planner already knows exactly what they need, and growing would allocate and could lose events a window needed.
- **One ring per instrument and kind of event.** The split happens anyway, in the stores. Before the hand-off it would cost two things: the reader only knows which ring a message goes to after decoding it, so it could no longer limit its poll to the free slots, and the evaluator would drain the rings in an order that depends on timing, so replays would not be reproducible.
- **A thread pool that runs the models.** Its queue allocates a task and a node per evaluation and takes locks, the models would read objects the evaluator is overwriting, and a stuck thread cannot be killed. The agent per model above keeps the goal without those costs.
- **Immutable objects, one allocated per event.** Safest to share, but it allocates on every event, against the hot-path rule.
- **Primitive columns behind the same API.** Better cache locality and nothing for the GC to trace, but the model-facing objects would have to be flyweights over them, for no gain that has been measured yet.
- **Keeping history in the Disruptor's ring.** One structure instead of two, but the producer overwrites slots the evaluator has passed, so windows would race with it.
- **Claiming with `tryNext()` and dropping when full.** Simple, but reports a full ring with an exception, which the hot path does not use for control flow. Limiting the poll avoids a full ring altogether.
