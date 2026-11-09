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

This is the initial design, written before the code and refined as it is built: **none of it exists yet**. Class and property names are provisional. The decisions and the alternatives discarded are in [ADR 0029](../../docs/adr/0029-the-engine-stores-every-event-and-models-define-their-windows.md).

The engine keeps every trade and every top-of-book update it receives. Each model declares the windows it wants over them, and how often it wants to be evaluated.

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

**Stores.** One per instrument and kind of event: trades, and top of book. Each one holds every event in arrival order, in pre-allocated objects.

- `initial` and `maximum`, configured per kind, limit the number of objects.
- The oldest object is overwritten when no window needs it any more. If a window still needs it, the store grows, up to `maximum`. Growing allocates, but only until the store reaches the size its windows need.
- At `maximum` the oldest object is overwritten anyway, and the windows that needed it report that they are incomplete.

**What models receive.** Each stored object is a mutable class (`MutableTrade`, `MutableBestBidAsk`) that implements a read-only interface (`Trade`, `BestBidAsk`). Models only ever see the interface, so several can share one object safely. The object is reused, so a reference is valid only during the evaluation that received it. A model that needs a value later copies the value.

**Windows.** A model declares them, per instrument and kind of event. A window copies no events: it is a range of positions over a store.

- By time or by number of events, sliding or tumbling.
- Time is always `exchangeTimestamp`, never the system clock. A window's "now" is the latest timestamp of its input and never goes backwards.
- Events are never reordered: a late event stays where it arrived, so a replay gives the same result as the original run.
- Bars are something a model may build from a window if it wants them. Another model can work on every tick instead, such as a mid price on each top-of-book update.

**Evaluation.** Each deployment chooses how often it runs: on every event of its inputs (the default), every N events, or every T of event time. A time-based evaluation only fires when an event arrives. A deployment on several instruments uses the latest value of the others and is told how old each one is ([ADR 0017](../../docs/adr/0017-multi-instrument-models-and-event-time.md)).

**Outputs.** Every `IndicatorValue` goes out on Aeron. For Kafka, the evaluator copies it into a ring drained by a separate sender, which conflates per deployment and instrument on a monotonic clock, as it only limits how often the topic is written ([ADR 0018](../../docs/adr/0018-output-policy-of-the-durable-path.md)). Kafka never delays the evaluator ([ADR 0005](../../docs/adr/0005-the-engine-never-blocks-on-kafka.md)).

### Order of work

| Task | What it builds |
| :--- | :--- |
| [#111](https://github.com/MeSeeFoo/argus-quant-engine/issues/111) | The reader, the Disruptor hand-off, the stores and the windows |
| [#112](https://github.com/MeSeeFoo/argus-quant-engine/issues/112) | The model contract, deployments from configuration, and when each one is evaluated |
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
