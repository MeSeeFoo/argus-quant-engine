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
Aeron poll ─▶ decode ─▶ checks ─▶ ring of reused slots ─▶ windows ─▶ models
```

- The **reader agent** polls Aeron, decodes with flyweights, drops old trades and old top-of-book updates and counts `sequence` gaps ([0022](0022-feed-gateway-shards-with-an-active-and-a-passive.md)). It publishes trades and top-of-book updates into **one** Disruptor ring, so the evaluator sees them in arrival order across instruments. It belongs to the inbound Aeron adapter, like the gateway's Kafka sender agent: polling is the adapter's business, and the application only sees what it hands over through `IngestMarketData`, so there is no polled source port ([0009](0009-hexagonal-architecture-enforced-with-archunit.md)). The checks and the hand-off to the ring are the application's use case.
- The **evaluator agent** drains the ring through an `EventPoller`, which keeps it an Agrona agent that Spring starts like any other ([0010](0010-spring-confined-to-the-edges-of-the-engine.md)). The windows and the models are used only on this thread, so they need no locks or memory barriers.
- **The reader never waits for the evaluator.** If data is in Aeron and the reader does not take it, the reader is waiting, even if its thread is not blocked. So it always drains Aeron, and a full ring is backpressure: the engine drops data, counts it, and a configured strategy says which:
  - **Drop newest.** The event that does not fit is dropped and counted, and the ring keeps what it holds. It is simple and entirely on the reader's side, but while the overload lasts the evaluator works on older data and the fresh data is thrown away.
  - **Drop oldest** (the default). The reader cannot overwrite the oldest slots itself: they are the ones the evaluator has not read, and it may be reading one at that moment. So the reader drops the event that does not fit, counts it and raises a flag. At the start of its next duty cycle the evaluator moves its sequence to the ring's cursor, skipping the whole backlog without evaluating it, and counts it; it does this on its own thread, between two polls, so it is safe. The ring is empty again and fresh data flows at once; only the few newest events that arrive before the evaluator reacts are lost. For models that react to the market, fresh data is worth more than complete data.
- The ring is the engine's only buffer against bursts, so its size is the setting that matters. A drop breaks the windows it falls into: a tumbling window of 100 then spans events that are not consecutive. Telling the models is left for later; the counters show it.
- The ring is only a hand-off. A slot is free again as soon as the evaluator has passed it, so nothing keeps a reference to one: each window copies the events it is given.
- The ring creates each slot once and only ever overwrites it, so a slot cannot change class. Since trades and top-of-book updates arrive mixed, each slot holds one reusable trade and one reusable top-of-book update, and a field says which is current.

### Set up from the models' configuration

At startup a planner reads the configuration of every deployment: which instruments and kinds of event (trades, top of book) it uses, and which windows. From that it creates each window and links it to its deployment, by instrument and kind of event. For each event, the evaluator appends it to the windows of its instrument and kind, and runs the deployment of every window that fires.

Each window keeps its own copies of its events, in objects it allocates once, when it is created:

| Window | Copies it keeps |
| :--- | :--- |
| Sliding, last N events | N, in a circular array: each new event overwrites the oldest |
| Tumbling, slices of N events | 2N, in two halves: the last closed slice stays readable while the next one fills, then they swap |

| Example | Windows |
| :--- | :--- |
| Model Z: tumbling window of 100 on A's top of book | one tumbling window of 100, 200 copies |
| Model Y: every update of B | two sliding windows of 1, on B's trades and on B's top of book, 1 copy each |
| Nobody uses A's trades | no window: A's trades are not kept |

- **Sizes are fixed at startup.** A window never grows, and never overwrites an event that its model can still read. Steady state allocates nothing.
- **A window that fires hands its model a `Window`:** a simple holder of the events, oldest first, and of its "now", created once per window and refilled when it fires, so firing allocates nothing. A record would be simpler, but it is immutable, so it would allocate one per firing.
- **Windows do not share their copies.** Two windows on the same instrument and kind each keep their own, which costs memory only for large windows declared more than once, and keeps every window independent of the others.
- **Objects are read-only to models, not immutable.** Each copy is a mutable class that implements a read-only interface (`Trade`, `BestBidAsk`), and models only ever receive the interface. It is one object with two faces, so reading it costs no extra allocation or copy. The evaluator overwrites the object when it is reused, so a reference is valid only during the evaluation it was received in. A model that needs a value later copies the value.

### Prices and quantities stay exact mantissas

- Models read prices and quantities as `Decimal64` mantissas: a `long` at a scale of 10⁻⁸, as on the wire ([0004](0004-canonical-message-contracts.md)). They are exact, and reading or comparing them allocates nothing.
- **No `BigDecimal` on the hot path.** It is immutable, so every value read and every operation would allocate. **No `double` for market data either:** it is not exact, and prices are money.
- **Python models get `decimal.Decimal`.** GraalPy maps a Java `long` to an exact Python `int`, but a Java `BigDecimal` would reach Python as an opaque foreign object, and calling its methods needs host access that the sandbox denies by default. So the `argus` SDK builds a Python `Decimal` from the mantissa, inside the sandbox ([0011](0011-user-models-in-python-on-graalpy.md)). Every Python number is an object anyway, so this costs nothing that Python would not already spend.

### Windows by number of events

- Each window keeps **its own copies** of its events, so it does not depend on the ring or on any other window.
- **Sliding:** the last N events, firing once full and then every `slide` events, from 1 (every event) to N. **Tumbling:** slices of N events that do not overlap; a model reads the last closed one.
- Windows by time are left for when a model needs them; they will need their own sizing rule, since how many events fit in a minute depends on the market.
- Events stay in arrival order and are never reordered, which keeps a replay identical to the original run. A window's "now" is the latest `exchangeTimestamp` of its input and never goes backwards.

### When a model is evaluated

This amends [0017](0017-multi-instrument-models-and-event-time.md), which evaluates a deployment on every update of any of its inputs. **A deployment is evaluated when one of its windows fires:** a sliding window every `slide` events (every event by default), a tumbling window when a slice closes. Evaluating on every update is a sliding window of one event. The deployment's other inputs are read as they are at that moment, as 0017 describes.

### Models run on the evaluator thread

- The evaluator runs a model as soon as one of its windows fires, then carries on with the next event. It calls it through an interface (`Model.evaluate`), with a read-only view of all the model's inputs (`ModelInputs`), so how models run can change without touching the rest.
- In Phase 2 the only models are the built-in ones ([0015](0015-built-in-models-in-java-behind-the-same-contract.md)): our own code, which takes microseconds. A model that hangs would block the evaluator; that is accepted for them.
- **Planned for Python models (Phase 3), to be decided with its own ADR:** an agent per model, or per group of models, fed by its own Disruptor of pre-allocated evaluation requests, each with a copy of the window. The model's agent is the only consumer, so evaluations keep their order, and how far it lags shows when it cannot keep up: that is where a circuit breaker fits. Outputs go back through a ring to a single publisher, which keeps `sequence` monotonic. Timeouts, cancellation and memory limits come from the GraalPy sandbox ([0011](0011-user-models-in-python-on-graalpy.md)): a Java thread cannot be killed safely, but a polyglot context can be interrupted or closed.

## Consequences

- Memory is exactly what the deployments need, and known at startup.
- Models can compute anything from raw events, including a mid price on every update.
- A new deployment changes what the engine keeps. In Phase 2 deployments come from configuration, so it means a restart; distributing them at runtime (Phase 3) will create and drop windows while the engine runs.
- Two threads plus the driver's, none pinned on Fargate ([0021](0021-run-everything-on-fargate-with-aeron-over-udp.md)).
- A slow model delays the other models, as they share the evaluator thread, but never the reading of the feed. Once the ring is full, the engine loses data, and counts exactly how much and why.
- The Disruptor is a second ring buffer library next to Agrona's, used only here. Part of the reason is a learning goal, stated as such, like Spring in [0010](0010-spring-confined-to-the-edges-of-the-engine.md).
- The gateway keeps Agrona's ring, for the reasons in `MarketDataQueue`: it has several producers, and its events never leave the service as objects.

## Alternatives considered

- **Fixed 1-second and 1-minute bars.** Small and bounded memory, but a model could not build its own window from them.
- **Stores that grow between a configured initial and maximum size.** Works without knowing the models, but the planner already knows exactly what they need, and growing would allocate and could lose events a window needed.
- **One store per instrument and kind, with windows as positions over it.** Windows on the same events would share one copy of each, but every window would depend on the store's size and on the other windows', for a memory saving that only matters for large windows declared twice.
- **A window that returns a new array of its events when it fires.** Simple to read, but it allocates on every firing.
- **One ring per instrument and kind of event.** The split happens anyway, when the evaluator hands each event to the windows of its instrument and kind. Before the hand-off it would cost two things: a burst on one instrument would fill its ring and drop data while the others are empty, and the evaluator would drain the rings in an order that depends on timing, so replays would not be reproducible.
- **A thread pool that runs the models.** Its queue allocates a task and a node per evaluation and takes locks, the models would read objects the evaluator is overwriting, and a stuck thread cannot be killed. The agent per model above keeps the goal without those costs.
- **A slot class per kind of event, with a common interface, cast when polled.** Fewer fields per slot, but a slot is created once with one class and never replaced, so it could not hold the other kind.
- **`BigDecimal` getters for models.** Familiar and exact, but they allocate on every read and every operation, and Python models could not use them through the sandbox.
- **Immutable objects, one allocated per event.** Safest to share, but it allocates on every event, against the hot-path rule.
- **Primitive columns behind the same API.** Better cache locality and nothing for the GC to trace, but the model-facing objects would have to be flyweights over them, for no gain that has been measured yet.
- **Keeping history in the Disruptor's ring.** One structure instead of two, but the producer overwrites slots the evaluator has passed, so windows would race with it.
- **Leaving in Aeron what does not fit.** Each poll would ask for no more messages than the ring has free slots, so Aeron's buffers would absorb bursts. But the reader would stop taking data that is there, and once the engine fell behind by more than those buffers, Aeron would drop it from the stream (`fc=max`): the loss would come later, and show only as a `sequence` gap, without knowing why.
- **The reader overwriting the oldest slots.** It is what drop oldest means, but those slots are the ones the evaluator has not read, so it could read a half-written event. Only the consumer can discard slots safely.
- **Claiming with `tryNext()` or `tryPublishEvent`.** They report a full ring with an exception, which the hot path does not use for control flow. The reader checks for a free slot first: it is the ring's only producer, so between the check and the claim the evaluator can only free slots.
