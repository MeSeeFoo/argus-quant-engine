# argus-model-engine

Runs model deployments on live market data and publishes their outputs on the fast path (Aeron) and the durable path (Kafka) ([ADR 0014](../../docs/adr/0014-one-model-engine-for-alert-rules-and-indicators.md)).

So far it is only the service's skeleton: it starts its agent and an Aeron driver, and has no work to do. Receiving market data, models and outputs arrive with the rest of story [2.3](https://github.com/MeSeeFoo/argus-quant-engine/issues/104), following the [design](#design) below.

## Structure

Ports and adapters as laid out in [ADR 0009](../../docs/adr/0009-hexagonal-architecture-enforced-with-archunit.md), checked by [`ArchitectureTest`](src/test/java/com/argus/modelengine/ArchitectureTest.java):

```text
com.argus.modelengine
├── domain                   Trade, BestBidAsk (read-only views) and their reused objects, EventStore, EventWindow, Instruments, the reader's checks
├── application              MarketDataReader (the reader's use case), MarketEventStores, ModelEngineAgent
│   ├── port.in              MarketDataSource (polled by the reader), IngestMarketData (what the source hands over)
│   └── port.out             Where data leaves (empty so far)
├── adapter
│   ├── in.aeron             AeronMarketDataSource: decodes the gateway's SBE stream
│   └── out.<technology>     Outbound adapters, each with its configuration (none so far)
└── config                   The agent, and the JVM's Media Driver and Aeron client
```

Spring builds the objects and starts the agent on its own thread through [`argus-agent-lifecycle`](../../libs/agent-lifecycle/README.md); it is never on the path of a message ([ADR 0010](../../docs/adr/0010-spring-confined-to-the-edges-of-the-engine.md)).

**Why the Aeron driver and client are in `config`.** There is one embedded Media Driver per JVM ([ADR 0021](../../docs/adr/0021-run-everything-on-fargate-with-aeron-over-udp.md)), and the engine uses Aeron in both directions: market data comes in and outputs go out. Adapters never depend on each other, so neither can own what both need. Each adapter will own its channels and streams, and only inject the client. The driver's threads back off when idle instead of spinning, as in the gateway.

## Design

The design of story 2.3, written before the code and refined as it is built. The reader, the stores and the windows exist (#111); the planner, the model contract and the outputs do not yet. The decisions and the alternatives discarded are in [ADR 0029](../../docs/adr/0029-models-declare-their-windows-and-the-engine-keeps-what-they-need.md).

Each model declares the windows it wants: over which instrument, which kind of event, and how many events. From that the engine works out at startup what to keep, and wakes each model when one of its windows fires.

```text
reader thread                   Disruptor          evaluator thread
─────────────                   ─────────          ────────────────
Aeron ─▶ decode ─▶ checks ─▶ ring of slots ─▶ stores ─▶ windows ─▶ models ─▶ outputs
                                               one per instrument          Aeron, and a ring
                                               and kind of event           to the Kafka sender
```

### Reader thread

An agent that polls the gateway's Aeron stream and decodes `Trade`, `BestBidAsk` and `Heartbeat` with SBE flyweights.

- **Old trades.** A trade whose `tradeId` is not newer than the last one of its instrument is dropped and counted ([ADR 0022](../../docs/adr/0022-feed-gateway-shards-with-an-active-and-a-passive.md)).
- **Gaps.** Jumps in `sequence` are counted. A publisher that restarts is a new session, not a gap.
- **Never waits.** Each poll asks Aeron for no more messages than the ring has free slots. What was not read stays in Aeron's buffers. If the evaluator falls behind for longer than those can hold, the engine loses data on its own side and sees it as a gap.

### The Disruptor

One ring of reused slots, with one producer and one consumer, carries trades and top-of-book updates in arrival order. It is only the hand-off between the two threads: a slot can be overwritten as soon as the evaluator has passed it, so nothing keeps a reference to one.

### Evaluator thread

An agent that drains the ring through the Disruptor's `EventPoller`, so Spring starts it like any other agent. The stores, the windows and the models are used only on this thread, so they need no locks.

**Stores.** One per instrument and kind of event: trades, and top of book. Each one keeps the latest events in arrival order, in pre-allocated objects, as many as its windows need and no more. The size is fixed at startup, so a store never grows and never overwrites an event that a window still needs.

**The planner** reads the deployments at startup and sizes the stores (#112). A sliding window of N events needs N; a tumbling window of N needs 2N − 1, because its last closed slice stays readable while the next one fills. A store nobody uses keeps a single object. For example:

| Deployment | Store | Size |
| :--- | :--- | :--- |
| Model Z: tumbling window of 100 on A's top of book | A, top of book | 199 |
| Model Y: every update of B | B, trades and B, top of book | 1 each |
| Nobody uses A's trades | A, trades | 1 |

**What models receive.** Each stored object is a mutable class (`MutableTrade`, `MutableBestBidAsk`) that implements a read-only interface (`Trade`, `BestBidAsk`). Models only ever see the interface, so several can share one object safely. The object is reused, so a reference is valid only during the evaluation that received it. A model that needs a value later copies the value.

**Windows.** A model declares them, per instrument and kind of event, by number of events. A window copies no events: it is a range of positions over a store.

- **Sliding:** the last N events. It fires on every event.
- **Tumbling:** slices of N events that do not overlap. The model reads the last closed slice, and the window fires when a slice closes.
- Windows by time are left for when a model needs them.
- A window's "now" is the latest `exchangeTimestamp` of its input, never the system clock, and never goes backwards. Events are never reordered, so a replay gives the same result as the original run.
- Bars are something a model may build from a window if it wants them. Another model can work on every tick instead, such as a mid price on each top-of-book update: a sliding window of one event.

**Evaluation.** A deployment runs when one of its windows fires, on the evaluator thread, through a `ModelRunner` interface (#112). In the example, model Z runs every 100 top-of-book updates of A, and model Y on every trade and top-of-book update of B. A deployment on several instruments reads its other inputs as they are at that moment, and is told how old each one is ([ADR 0017](../../docs/adr/0017-multi-instrument-models-and-event-time.md)). The built-in models take microseconds; Python models, which may be slow or hang, will get an agent of their own in Phase 3 (see the ADR).

**Outputs.** Every `IndicatorValue` goes out on Aeron. For Kafka, the evaluator copies it into a ring drained by a separate sender, which conflates per deployment and instrument on a monotonic clock, as it only limits how often the topic is written ([ADR 0018](../../docs/adr/0018-output-policy-of-the-durable-path.md)). Kafka never delays the evaluator ([ADR 0005](../../docs/adr/0005-the-engine-never-blocks-on-kafka.md)).

### Order of work

| Task | What it builds |
| :--- | :--- |
| [#111](https://github.com/MeSeeFoo/argus-quant-engine/issues/111) | The reader, the Disruptor hand-off, the stores and the windows |
| [#112](https://github.com/MeSeeFoo/argus-quant-engine/issues/112) | The model contract, deployments from configuration, the planner that sizes the stores, and running each model when its windows fire |
| [#113](https://github.com/MeSeeFoo/argus-quant-engine/issues/113) | Outputs on Aeron |
| [#114](https://github.com/MeSeeFoo/argus-quant-engine/issues/114) | Conflated outputs to Kafka |

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
