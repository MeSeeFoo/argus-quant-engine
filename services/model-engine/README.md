# argus-model-engine

Runs model deployments on live market data and publishes their outputs on the fast path (Aeron) and the durable path (Kafka) ([ADR 0014](../../docs/adr/0014-one-model-engine-for-alert-rules-and-indicators.md)).

It is being built in story [2.3](https://github.com/MeSeeFoo/argus-quant-engine/issues/104), following the [design](#design) below. The reader agent subscribes to the gateway's Aeron stream, and the reader's checks, the Disruptor ring, the windows and the evaluator are wired behind it. The engine runs no deployment yet, so the market data it reads reaches no model.

## Structure

Ports and adapters as laid out in [ADR 0009](../../docs/adr/0009-hexagonal-architecture-enforced-with-archunit.md), checked by [`ArchitectureTest`](src/test/java/com/argus/modelengine/ArchitectureTest.java):

```text
com.argus.modelengine
├── domain                   One package per concept, plain Java:
│   ├── instrument           Instrument, Instruments (found from the wire's bytes without allocating)
│   ├── marketdata           MarketEvent, Trade, BestBidAsk (read-only views), MutableTrade, MutableBestBidAsk (their reused objects), AggressorSide, MarketEventType
│   ├── feed                 Deduplicator, SequenceGapDetector: the reader's checks, which decide and leave the counting to the application
│   ├── window               EventWindow (SlidingWindow, TumblingWindow), Window (the view models read), WindowDefinition
│   └── model                ModelConfiguration and its InputDefinitions (what a model declares), Model, ModelInputs (what it reads) and MutableModelInputs, DeployedModel and DeployedInput (a model bound to instruments)
├── application              MarketDataService and MarketDataEventChecker (the reader's use case), MarketDataSlot, BackpressureStrategy and RingOverflowMechanism (a full ring), Evaluator (the evaluator agent), MarketDataCounters (what the metrics read)
│   ├── port.in              IngestMarketData (what the inbound adapter hands over)
│   └── port.out             Where data leaves (empty so far)
├── adapter
│   ├── in.aeron             AeronMarketDataSource (decodes the gateway's SBE stream, and its reader agent), AeronInputConfiguration and AeronInputProperties (the subscription)
│   │   └── decoder          DecodedTrade, DecodedBestBidAsk: read-only views over the SBE decoders
│   └── out.<technology>     Outbound adapters, each with its configuration (none so far)
└── config                   The instruments, the reader's use case, the ring and the evaluator agent, and the JVM's Media Driver and Aeron client
```

Spring builds the objects and starts the two agents, each on its own thread, through [`argus-agent-lifecycle`](../../libs/agent-lifecycle/README.md): `config` starts the evaluator, and the Aeron adapter's own configuration starts the reader. Spring is never on the path of a message ([ADR 0010](../../docs/adr/0010-spring-confined-to-the-edges-of-the-engine.md)).

**Why the reader agent belongs to the adapter.** Polling Aeron is the adapter's own business, like the gateway's Kafka sender agent: the application only sees what the adapter hands over, through `IngestMarketData`. A `MarketDataSource` port that the application polled would only add an indirection, since nothing in the application decides when or how much to read.

**Why the Aeron driver and client are in `config`.** There is one embedded Media Driver per JVM ([ADR 0021](../../docs/adr/0021-run-everything-on-fargate-with-aeron-over-udp.md)), and the engine uses Aeron in both directions: market data comes in and outputs go out. Adapters never depend on each other, so neither can own what both need. Each adapter will own its channels and streams, and only inject the client. The driver's threads back off when idle instead of spinning, as in the gateway.

## Design

The design of story 2.3, written before the code and refined as it is built. The reader agent on Aeron, its checks, the hand-off, the windows and the evaluator exist (#111); the model outputs and the deployments from configuration do not yet. The decisions and the alternatives discarded are in [ADR 0029](../../docs/adr/0029-models-declare-their-windows-and-the-engine-keeps-what-they-need.md).

Each model declares the windows it wants: over which instrument, which kind of event, and how many events. From that the engine works out at startup what to keep, and wakes each model when one of its windows fires.

```text
reader thread                   Disruptor          evaluator thread
─────────────                   ─────────          ────────────────
Aeron ─▶ decode ─▶ checks ─▶ ring of slots ─▶ windows ─▶ models ─▶ outputs
                                               of the event's        Aeron, and a ring
                                               instrument and kind   to the Kafka sender
```

### Reader thread

An agent that polls the gateway's Aeron stream, up to 256 messages per duty cycle, like the evaluator, and decodes `Trade`, `BestBidAsk` and `Heartbeat` with SBE flyweights. It subscribes to `argus.model-engine.aeron.channel`, which registers with the gateway's control endpoint, and `stream-id`, the gateway's shard.

- **Old events.** A trade whose `tradeId`, or a top-of-book update whose `bookUpdateId`, is not newer than the last one of its instrument is dropped and counted ([ADR 0022](../../docs/adr/0022-feed-gateway-shards-with-an-active-and-a-passive.md)).
- **Gaps.** Jumps in `sequence` are counted. A publisher that restarts is a new session, not a gap. The engine never looks for gaps in a venue's own ids, such as Binance's consecutive `tradeId`: that would couple it to each venue, so it is the job of that venue's adapter in the gateway ([ADR 0031](../../docs/adr/0031-the-engine-detects-lost-market-data-from-the-gateways-sequence-only.md)).
- **Never waits.** Data in Aeron that the reader does not take is the reader waiting, even with its thread free. So the reader always drains Aeron, and a full ring drops data with a [backpressure strategy](#backpressure).

### The Disruptor

One ring of reused slots, with one producer and one consumer, carries trades and top-of-book updates in arrival order. It is only the hand-off between the two threads: a slot can be overwritten as soon as the evaluator has passed it, so nothing keeps a reference to one.

The ring creates each slot once and only overwrites it, so a slot cannot change class. Trades and top-of-book updates arrive mixed, so each [`MarketDataSlot`](src/main/java/com/argus/modelengine/application/MarketDataSlot.java) holds one `MutableTrade` and one `MutableBestBidAsk`, and its `kind` says which one is current.

#### Backpressure

When the evaluator falls behind, the ring fills, and the engine has to lose data: the ring is its only buffer, so `ring-size` is the setting that matters. [`BackpressureStrategy`](src/main/java/com/argus/modelengine/application/BackpressureStrategy.java), set with `argus.model-engine.backpressure`, says what is lost, and [`RingOverflowMechanism`](src/main/java/com/argus/modelengine/application/RingOverflowMechanism.java) applies it and counts the losses in [`MarketDataCounters`](src/main/java/com/argus/modelengine/application/MarketDataCounters.java), with the evaluator's failed evaluations. Both strategies allocate nothing.

- **`drop-newest`.** The event that does not fit is dropped and counted; the ring keeps what it holds. Simple and entirely on the reader's side, but while the overload lasts the evaluator works on older data and the fresh data is thrown away.
- **`drop-oldest`** (the default). The reader cannot overwrite the oldest slots: the evaluator has not read them and may be reading one right now. So the reader drops the event that does not fit, counts it and raises a flag. At the start of its next duty cycle the evaluator moves its sequence to the ring's cursor, skipping the whole backlog without evaluating it, and counts it. That is safe because the evaluator does it on its own thread, between two polls. The ring is empty again and fresh data flows at once; only the few newest events that arrive before the evaluator reacts are lost.

A drop breaks the windows it falls into: a tumbling window of 100 then spans events that are not consecutive. For now the counters show it; telling the models comes later.

### Evaluator thread

An agent that drains the ring through the Disruptor's `EventPoller`, so Spring starts it like any other agent. The windows and the models are used only on this thread, so they need no locks.

For each event it polls, the evaluator appends it to every window of the event's instrument and kind. [`EventWindow.append`](src/main/java/com/argus/modelengine/domain/window/EventWindow.java) returns the window's view when it fires, and `null` otherwise; the evaluator then runs the window's model with all its inputs.

At startup the [`Evaluator`](src/main/java/com/argus/modelengine/application/Evaluator.java) takes the deployed models: each a `DeployedModel`, the model and its inputs joined with the instruments the deployment chose. For each one it creates a window per input and one `MutableModelInputs` with their views, and subscribes each window to its instrument and kind of event. The subscriptions live in one table, indexed by `MarketEventType.index()` and then by instrument id, so routing an event is two array lookups, and a new kind of event needs no new table. Two deployments of the same model have windows and inputs of their own.

**Each window keeps its own copies.** The event it is given is a ring slot, which the ring overwrites as soon as the evaluator moves on, so a window copies it into objects it allocated once, when it was created. A window never grows, and never overwrites an event its model can still read:

| Window | Copies it keeps | Fires |
| :--- | :--- | :--- |
| Sliding, last N events | N, in a circular array: each new event overwrites the oldest | once full, then every `slide` events (every event by default) |
| Tumbling, slices of N events | 2N, in two halves: the last closed slice stays readable while the next one fills, then they swap | when a slice closes |

| Deployment | Windows |
| :--- | :--- |
| Model Z: tumbling window of 100 on A's top of book | one tumbling window of 100, with 200 copies |
| Model Y: every update of B | two sliding windows of 1, on B's trades and on B's top of book |
| Nobody uses A's trades | no window: A's trades are not kept |

**What models receive.** A [`Window`](src/main/java/com/argus/modelengine/domain/window/Window.java): a very simple class with record-style accessors, `events()`, the array of events oldest first, and `now()`. A window fires only once it is full, so the array always has the window's count of events; before its first firing it is empty. Each window creates its `Window` once and refills it when it fires, so firing allocates nothing. It is a class rather than a record because a record is immutable: a new one per firing would allocate. A model with several inputs may keep the `Window` of each and read the others when one fires. Each event is a mutable class (`MutableTrade`, `MutableBestBidAsk`) seen through a read-only interface (`Trade`, `BestBidAsk`). Both are reused, so they are valid only during the evaluation that received them. A model that needs a value later copies the value.

**Prices and quantities** are exact `Decimal64` mantissas (a `long` at a scale of 10⁻⁸), as on the wire: `priceMantissa()`, `bidPriceMantissa()` and so on. A `BigDecimal` would allocate on every read and every operation, and a `double` is not exact. Python models will get them as Python `decimal.Decimal` values, built by the SDK from the mantissa, which reaches Python as an exact `int` (ADR 0029).

**What a model declares.** A [`ModelConfiguration`](src/main/java/com/argus/modelengine/domain/model/ModelConfiguration.java), immutable and built with a builder, lists its inputs. Each input has a name, a kind of event and a window. The name is also the slot a deployment fills with an instrument: a model is a template, and its instruments are chosen when it is deployed ([ADR 0014](../../docs/adr/0014-one-model-engine-for-alert-rules-and-indicators.md)). Model Z of the example:

```java
ModelConfiguration.builder()
    .input("quotes", MarketEventType.BEST_BID_ASK, WindowDefinition.tumbling(100))
    .build();
```

And a deployment of it, from configuration until the UI can create them (#112):

```yaml
deployments:
  - id: 1
    model: model-z
    instruments:
      quotes: EUREX:SAN   # input name -> instrument
```

Two inputs on the same instrument, such as a model's trades and top of book, are each bound to it.

**Windows.** A model declares them, per instrument and kind of event, by number of events.

- **Sliding:** the last N events. It fires once full, then every `slide` events: `sliding(100)` on every event, `sliding(100, 10)` every 10 events, on the last 100. Between firings its view still holds the last N events in order, so a model reading it when another of its inputs fires never sees a mix.
- **Tumbling:** slices of N events that do not overlap. The model reads the last closed slice, and the window fires when a slice closes.
- Windows by time are left for when a model needs them.
- A window's "now" is the latest `exchangeTimestamp` of its input, never the system clock, and never goes backwards. Events are never reordered, so a replay gives the same result as the original run.
- Bars are something a model may build from a window if it wants them. Another model can work on every tick instead, such as a mid price on each top-of-book update: a sliding window of one event.

**Evaluation.** A deployment runs when one of its windows fires, on the evaluator thread: the evaluator records which input fired and calls `Model.evaluate(ModelInputs)`, where the model reads each input with `get(name)`, `trades(name)` or `bestBidAsks(name)`. A model that throws is counted and the evaluator carries on. Deployments from configuration arrive with #112. In the example, model Z runs every 100 top-of-book updates of A, and model Y on every trade and top-of-book update of B. A deployment on several instruments reads its other inputs as they are at that moment, and is told how old each one is ([ADR 0017](../../docs/adr/0017-multi-instrument-models-and-event-time.md)). The built-in models take microseconds; Python models, which may be slow or hang, will get an agent of their own in Phase 3 (see the ADR).

**Outputs.** Every `IndicatorValue` goes out on Aeron. For Kafka, the evaluator copies it into a ring drained by a separate sender, which conflates per deployment and instrument on a monotonic clock, as it only limits how often the topic is written ([ADR 0018](../../docs/adr/0018-output-policy-of-the-durable-path.md)). Kafka never delays the evaluator ([ADR 0005](../../docs/adr/0005-the-engine-never-blocks-on-kafka.md)).

### Order of work

| Task | What it builds |
| :--- | :--- |
| [#111](https://github.com/MeSeeFoo/argus-quant-engine/issues/111) | The reader, the Disruptor hand-off, the windows, and the evaluator that runs each model when one of its windows fires |
| [#112](https://github.com/MeSeeFoo/argus-quant-engine/issues/112) | The model contract with its outputs and parameters, deployments from configuration, and a test model |
| [#113](https://github.com/MeSeeFoo/argus-quant-engine/issues/113) | Outputs on Aeron |
| [#114](https://github.com/MeSeeFoo/argus-quant-engine/issues/114) | Conflated outputs to Kafka |

## Logs

Nothing is logged per event: a log call with parameters allocates and may write to disk, so on the hot path it would break the zero-allocation rule. The counters are the signal; the logs are for what someone has to react to. A problem that repeats is logged with [`argus-rate-limited-log`](../../libs/rate-limited-log/README.md): at most one line every 10 seconds, with the count so far, and nothing allocated while a line is skipped.

| What | Level | Thread | When |
| :--- | :--- | :--- | :--- |
| Instruments, ring size and backpressure strategy, the Aeron channel and stream, deployed models and windows | INFO | Spring's, at startup | Once |
| A gateway session that connects, or leaves | INFO, WARN | The Aeron client's conductor | Each time: with an active and a passive gateway, this is how a failover shows |
| Events the ring lost, dropped by the reader or skipped by the evaluator | WARN | Evaluator | At most once per interval, so the reader never logs them |
| A model that throws, with its exception | ERROR | Evaluator | At most once per interval for each deployment, so one broken model never hides another |
| A gap in `sequence`; a message of an unknown instrument, with an invalid side, or of another schema or template | WARN | Reader | At most once per interval for each kind |

Duplicate and old trades are only counted: they are expected while a passive gateway takes over.

## Running it next to the gateway

Start the [feed gateway](../feed-gateway/README.md#running-it) as its README says, then, in a second terminal:

```bash
./mvnw -pl services/model-engine -am install -DskipTests
./mvnw -pl services/model-engine spring-boot:run
```

Actuator listens on port **8082** (8080 is Apicurio in the [local environment](../../README.md#-local-environment) and 8081 the gateway): `http://localhost:8082/actuator/health`. Each JVM embeds its own driver in a generated directory of its own, so both run on one machine without sharing anything. Setting `-Daeron.dir` replaces that generated name: never give both JVMs the same value, because each driver deletes the directory on start.

### JVM flags

Agrona needs `--add-exports java.base/jdk.internal.misc=ALL-UNNAMED` on Java 25:

- `spring-boot:run` passes it (`agrona.jvm.args` in the parent POM).
- `java -jar target/argus-model-engine-*.jar` needs nothing: the jar's manifest declares `Add-Exports: java.base/jdk.internal.misc`.
- Any other launch (an IDE run configuration, a container entry point) must pass the flag itself.
