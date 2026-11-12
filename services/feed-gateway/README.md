# argus-feed-gateway

Receives market data from a venue and publishes it on the fast path (Aeron + SBE) in the [canonical format](../../contracts/README.md). In Phase 2 it reads BTCUSDT from Binance, with a single shard and a single task ([ADR 0022](../../docs/adr/0022-feed-gateway-shards-with-an-active-and-a-passive.md), [ADR 0023](../../docs/adr/0023-instrument-catalogue.md)).

## Structure

Ports and adapters as laid out in [ADR 0009](../../docs/adr/0009-hexagonal-architecture-enforced-with-archunit.md), checked by [`ArchitectureTest`](src/test/java/com/argus/feedgateway/ArchitectureTest.java):

```text
com.argus.feedgateway
├── domain                   Trade, BestBidAsk (mutable, reused), Instrument(s), Decimal64, BookUpdateFilter
├── application              MarketDataQueue (where data enters) with TradeSlot and BidAskSlot (its layout), MarketDataRelay (the use case), FeedAgent (the thread that runs it), MarketDataCounters
│   ├── port.in              IngestMarketData: decoded market data, queued for the agent
│   └── port.out             MarketDataPublisher: one per transport, fast and durable path
├── adapter
│   ├── in.binance           BinanceStreamClient + BinanceStreamDecoder, with their configuration
│   ├── out                  PublicationCounters and PublicationMetrics, shared by the outbound adapters
│   ├── out.aeron            AeronMarketDataPublisher, with its configuration
│   ├── out.kafka            KafkaMarketDataPublisher with TradeCodec and BestBidAskCodec (its ring's layouts), with its configuration
│   └── out.kafka.mapping    ProtobufMappers: events to the canonical Protobuf messages
├── config                   Instruments, the use case and the agent: nothing about Binance, Aeron or Kafka
└── utils                    Usable from any package: FailureLog (a throttled error log)
```

A frame's journey. The adapter decodes on the connection's thread and hands the events over; from the queue on, everything runs on the gateway agent's thread:

```text
HTTP client threads                                 gateway agent's thread
───────────────────                                 ──────────────────────
Binance ──WebSocket──▶ BinanceStreamClient          FeedAgent.doWork()
                       └─ BinanceStreamDecoder        └─ MarketDataQueue.drain()
                          fills a reused Trade           └─ MarketDataRelay
                          └─ IngestMarketData   port.in     └─ MarketDataPublisher   port.out
                             = MarketDataQueue ─ring─┘         └─ Aeron, over UDP
```

The adapter only deals with the connection and the venue's format. Queueing, and what to do when the queue is full, belong to the application, so a different connection technology or venue does not touch them.

Spring builds the objects and starts the agent on its own thread through an Agrona `AgentRunner` ([`argus-agent-lifecycle`](../../libs/agent-lifecycle/README.md)); it is never on the path of a message ([ADR 0010](../../docs/adr/0010-spring-confined-to-the-edges-of-the-engine.md)). Each adapter owns its properties and its Spring configuration; `config` only sees the ports.

## The gateway

| Property | Default | Meaning |
| :--- | :--- | :--- |
| `argus.feed-gateway.instruments` | `BINANCE:BTCUSDT` | The shard's instruments as `VENUE:SYMBOL`, until the instrument catalogue arrives ([ADR 0023](../../docs/adr/0023-instrument-catalogue.md)) |
| `argus.feed-gateway.heartbeat-interval` | `1s` | Idle time before a `Heartbeat` |

[`MarketDataQueue`](src/main/java/com/argus/feedgateway/application/MarketDataQueue.java) implements `IngestMarketData`, and is where market data enters:

- **Thread-safe, never waits.** Adapters call it from their own threads, several at once. Each event is copied into a pre-allocated ring buffer (1 MiB, room for about fifteen thousand events) in a fixed layout, with the instrument as its index: [`TradeSlot`](src/main/java/com/argus/feedgateway/application/TradeSlot.java) and [`BidAskSlot`](src/main/java/com/argus/feedgateway/application/BidAskSlot.java) copy the fields in and out. When the ring is full the event is dropped and counted. The class comment explains why the ring holds bytes and not reusable objects, as the LMAX Disruptor would.
- **Rejections that need no state.** A trade or top-of-book update for an instrument the gateway does not relay, or whose prices or quantities do not fit `Decimal64`, is rejected before it is queued, as a whole and never rounded.

[`MarketDataRelay`](src/main/java/com/argus/feedgateway/application/MarketDataRelay.java) is the use case, fed by the queue:

- **Stale updates.** A top-of-book update not newer than the last one of its instrument is dropped ([`BookUpdateFilter`](src/main/java/com/argus/feedgateway/domain/BookUpdateFilter.java)). This check keeps state per instrument, so it runs on the agent's thread. Every rejection is counted in [`MarketDataCounters`](src/main/java/com/argus/feedgateway/application/MarketDataCounters.java).
- **Heartbeats.** When nothing was published during a whole interval, it publishes a `Heartbeat`, so an idle stream carries one every one to two intervals. The checks run on a monotonic clock, so a wall clock stepping backwards cannot silence the heartbeat; the message carries the epoch clock.

[`FeedAgent`](src/main/java/com/argus/feedgateway/application/FeedAgent.java) is the gateway's only hot-path thread. On each duty cycle it drains up to 64 events from the queue into the relay, then lets the relay publish a heartbeat if the stream was idle. When there is no work, a back-off idle strategy waits instead of spinning.

Events travel as reused, mutable [`Trade`](src/main/java/com/argus/feedgateway/domain/Trade.java) and [`BestBidAsk`](src/main/java/com/argus/feedgateway/domain/BestBidAsk.java) objects: the decoder fills one per event, the queue fills its own when draining, and whoever receives one reads it before returning. Nothing is allocated per message on the whole path, which [`BinanceStreamDecoderAllocationTest`](src/test/java/com/argus/feedgateway/adapter/in/binance/BinanceStreamDecoderAllocationTest.java) checks with the JVM's per-thread allocation counter.

## Inbound adapter: Binance

The gateway reads Binance's [SBE market data stream](https://developers.binance.com/docs/binance-spot-api-docs/sbe-market-data-streams): the JSON `@bookTicker` stream carries no event time, which the canonical `BestBidAsk` needs ([ADR 0017](../../docs/adr/0017-multi-instrument-models-and-event-time.md)). The adapter only reads `BINANCE:` instruments.

**The connection.** [`BinanceStreamClient`](src/main/java/com/argus/feedgateway/adapter/in/binance/BinanceStreamClient.java) opens one WebSocket, with the JDK's own client, to the combined stream of every instrument: `wss://stream-sbe.binance.com:9443/stream?streams=btcusdt@trade/btcusdt@bestBidAsk`.

- **API key.** It goes in the `X-MBX-APIKEY` header and comes from the `BINANCE_API_KEY` environment variable. It is never logged or committed, and the gateway refuses to start without it. Binance only accepts Ed25519 keys for this stream; a read-only key is enough.
- **Pings.** Binance pings every 20 seconds and closes the connection after a minute without a pong. The JDK client answers each ping with a pong carrying its payload.
- **Text frames.** Subscription responses and `serverShutdown` events are logged for now. Reconnecting is story [2.8](https://github.com/MeSeeFoo/argus-quant-engine/issues/66).
- **From the connection to the gateway.** The connection's callbacks run on the HTTP client's threads. Frames that arrive in parts are put back together first; a frame larger than 128 KiB is dropped and counted. Each complete frame is decoded there, with its receive time, and its events are handed to `IngestMarketData`, which never waits.

**Decoding.** [`BinanceStreamDecoder`](src/main/java/com/argus/feedgateway/adapter/in/binance/BinanceStreamDecoder.java) decodes `TradesStreamEvent` and `BestBidAskStreamEvent` with flyweights, on the connection's thread. Each connection has its own decoder, so two connections never share one. One trades event can carry several trades, each handed over on its own. Other messages, other schemas, malformed frames and symbols outside the shard are counted in [`BinanceCounters`](src/main/java/com/argus/feedgateway/adapter/in/binance/BinanceCounters.java) and ignored.

- **Binance's codecs stay here.** [`src/main/sbe/binance/stream_1_0.xml`](src/main/sbe/binance/stream_1_0.xml) is Binance's stream schema, copied unchanged from [binance-spot-api-docs](https://github.com/binance/binance-spot-api-docs/blob/master/sbe/schemas/stream_1_0.xml).
- **Generated at build time.** `sbe-tool` generates its codecs into `com.argus.feedgateway.adapter.in.binance.sbe`; nothing outside the gateway sees them ([ADR 0004](../../docs/adr/0004-canonical-message-contracts.md)). The schema's own warnings (a `uint32` group count) are not fatal for this schema only.

| Canonical | From Binance |
| :--- | :--- |
| `Trade.exchangeTimestamp` | `transactTime` (µs → ns) |
| `BestBidAsk.exchangeTimestamp` | `eventTime` (µs → ns) |
| Prices and quantities | mantissa and exponent → `Decimal64` (exponent −8), exact or not representable ([`Decimal64`](src/main/java/com/argus/feedgateway/domain/Decimal64.java)) |
| `aggressorSide` | `isBuyerMaker` true → `SELL`, false → `BUY` |
| `bookUpdateId` | `bookUpdateId` |

Tests build synthetic frames with Binance's encoders, and run the client against a local WebSocket server: no real market data is committed, and CI needs no key.

| Property | Default | Meaning |
| :--- | :--- | :--- |
| `argus.feed-gateway.binance.uri` | `wss://stream-sbe.binance.com:9443` | Base address of the stream |
| `argus.feed-gateway.binance.api-key` | `${BINANCE_API_KEY}` | Read-only Ed25519 API key |

## Outbound adapter: Aeron

[`AeronMarketDataPublisher`](src/main/java/com/argus/feedgateway/adapter/out/aeron/AeronMarketDataPublisher.java) implements the `MarketDataPublisher` port: it encodes canonical `Trade`, `BestBidAsk` and `Heartbeat` messages from [`argus-contracts`](../../contracts/README.md) and offers them to an Aeron publication.

- **Transport** ([ADR 0021](../../docs/adr/0021-run-everything-on-fargate-with-aeron-over-udp.md)):
  - an embedded Media Driver in its own directory, whose threads back off instead of spinning;
  - one `ExclusivePublication` per shard, over UDP with a dynamic multi-destination-cast control endpoint, where subscribers register. Flow control is `fc=max`: the fastest subscriber sets the pace, and a slower one loses data on its own side (it sees gaps) instead of slowing the gateway down. Those losses are the subscriber's, not counted here.
- **Sequence.** `Trade` and `BestBidAsk` share one `sequence`, starting at 1, assigned by `MarketDataRelay` so that every transport numbers a message the same. A `Heartbeat` carries the last one (0 before the first message).
- **Never blocks.** When Aeron refuses a message (no subscriber, back-pressure, a closed publication), the message is dropped and counted. It keeps its sequence, so subscribers see the loss as a gap. The one retry is immediate and single, after `ADMIN_ACTION`: the offer reached the end of a term and rotated it without writing the message.
- **Threading and allocation.** Single-threaded and allocation-free, including when subscribers fall behind ([`AeronMarketDataPublisherTest`](src/test/java/com/argus/feedgateway/adapter/out/aeron/AeronMarketDataPublisherTest.java) runs a real driver and subscriber).

| Property | Default | Meaning |
| :--- | :--- | :--- |
| `argus.feed-gateway.aeron.channel` | `aeron:udp?control=localhost:40456\|control-mode=dynamic\|fc=max` | Publication channel. Subscribers use `aeron:udp?endpoint=<their host>:0\|control=<this control endpoint>\|control-mode=dynamic` |
| `argus.feed-gateway.aeron.stream-id` | `1001` | Stream of shard 1 |

## Outbound adapter: Kafka

The gateway publishes market data on the durable path too ([ADR 0027](../../docs/adr/0027-the-feed-gateway-publishes-market-data-on-both-paths.md)), as the canonical Protobuf `Trade` and `BestBidAsk` of [`argus-contracts`](../../contracts/README.md), with schemas in Apicurio ([ADR 0008](../../docs/adr/0008-apicurio-as-schema-registry-in-every-environment.md)).

- **Never blocks the agent** ([ADR 0005](../../docs/adr/0005-the-engine-never-blocks-on-kafka.md)). [`KafkaMarketDataPublisher`](src/main/java/com/argus/feedgateway/adapter/out/kafka/KafkaMarketDataPublisher.java) only copies each event into a ring buffer of its own, in the layout of [`TradeCodec`](src/main/java/com/argus/feedgateway/adapter/out/kafka/TradeCodec.java) or [`BestBidAskCodec`](src/main/java/com/argus/feedgateway/adapter/out/kafka/BestBidAskCodec.java): its sequence, its fields, and the instrument's id, read back from the shard's `Instruments` with its venue and symbol. When that ring is full the event is dropped and counted. A separate publisher thread drains the ring and sends to Kafka.
- **Mapping.** [`ProtobufMappers`](src/main/java/com/argus/feedgateway/adapter/out/kafka/mapping/ProtobufMappers.java) maps the events read back from the ring to the Protobuf messages, on the publisher thread. Prices and quantities are copied as they are: they are already `Decimal64` mantissas at the contract's scale. The sequence is the one of the fast path, so a consumer can match both. `aggressorSide` maps to `SIDE_BUY` or `SIDE_SELL`, never `SIDE_UNSPECIFIED`.
- **Every trade, and the conflated top of book.** Every trade goes to Kafka, because candles need all of them. A top of book replaces the previous one and changes many times per millisecond, while the archive and the UI only need its recent state, so `argus.market.quotes.v1` carries the latest of each instrument at most once per interval, and only if there was an update since the last one sent. The publisher thread keeps one pre-allocated slot per instrument, and sends the ones that changed when the interval ends. Aeron still carries every update.
- **The interval is not event time.** It only limits how often the topic is written, so it runs on a monotonic clock read by the publisher thread: venues' clocks do not affect it, and the last change still goes out when the market stops. Each message keeps its own `exchangeTimestamp`. On shutdown, whatever is waiting is sent without waiting for the interval.
- **Topics and key.** `argus.market.trades.v1` and `argus.market.quotes.v1` ([`topics.conf`](../../infra/docker/kafka/topics.conf)), keyed by `VENUE:SYMBOL`, so each instrument stays in order within its partition.
- **No heartbeats.** `Heartbeat` stays on the fast path: Kafka has its own liveness mechanisms.
- **Acknowledgements and failures.** A record counts as published when Kafka acknowledges it: the adapter's `ProducerListener`, which Spring Boot plugs into its `KafkaTemplate`, counts acknowledgements and failures. A record that cannot even be handed to Kafka is counted as failed too, and the sender goes on with the next. Failures are logged at most once every 10 s, so an outage does not flood the log.
- **Serialization.** Keys as text and values with Apicurio's `ProtobufKafkaSerializer`, which registers the schemas on first use only in the local environment (`APICURIO_AUTO_REGISTER`, true by default).

| Property | Default | Meaning |
| :--- | :--- | :--- |
| `argus.feed-gateway.kafka.trade-topic-name` | `argus.market.trades.v1` | Topic of every `Trade` |
| `argus.feed-gateway.kafka.best-bid-ask-topic-name` | `argus.market.quotes.v1` | Topic of the conflated `BestBidAsk` |
| `argus.feed-gateway.kafka.best-bid-ask-interval` | `1s` | How often, at most, the top of book of each instrument reaches Kafka, and only if it changed. Measured on a monotonic clock read by the Kafka sender: it limits the rate, it is not event time |
| `argus.feed-gateway.kafka.buffer-capacity` | `1048576` | Bytes of the ring between the gateway agent and the Kafka sender, a power of two |
| `argus.feed-gateway.kafka.sender-idle-sleep` | `5ms` | How long the Kafka sender parks when the ring is empty: the most an event waits before it is handed to Kafka |
| `spring.kafka.bootstrap-servers` | `${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}` | Kafka of the local environment |
| `spring.kafka.producer.properties.apicurio.registry.url` | `${APICURIO_REGISTRY_URL:http://localhost:8080/apis/registry/v3}` | Apicurio of the local environment |

## Metrics

At `/actuator/metrics`:

| Metric | Tag | Meaning |
| :--- | :--- | :--- |
| `argus.feed.messages.published` | `type`: `trade`, `best_bid_ask`, `heartbeat` (Aeron only); `transport`: `aeron`, `kafka` | Messages handed to each transport |
| `argus.feed.messages.dropped` | `reason` and `transport`: `queue_full` with `all` (gateway, before any transport); `not_connected`, `back_pressured`, `closed`, `other` with `aeron`; `ring_full`, `send_failed` with `kafka` | Messages dropped instead of blocking |
| `argus.feed.messages.rejected` | `reason`: `unsupported`, `unknown_symbol` (Binance adapter); `unrepresentable`, `stale_book`, `unknown_instrument` (gateway) | Venue messages received but not published |
| `argus.feed.frames.dropped` | | Frames dropped on arrival because they were larger than accepted |

## Running it

You need a Binance API key: create an Ed25519 key with only *Enable Reading* in Binance's API Management, and export its API key (never the private key, which Argus does not use):

```bash
export BINANCE_API_KEY=...
./mvnw -pl services/feed-gateway -am install -DskipTests
./mvnw -pl services/feed-gateway spring-boot:run
```

Actuator listens on port **8081** (8080 is Apicurio in the [local environment](../../README.md#-local-environment)): `http://localhost:8081/actuator/health`.

If the connection fails with `PKIX path building failed` on Windows, the JDK does not trust a certificate authority that Windows does (an antivirus or a corporate proxy that inspects TLS): start the JVM with `-Djavax.net.ssl.trustStoreType=Windows-ROOT`. An `Unexpected HTTP response status code 400` means Binance rejected the API key.

### Checking the fast path by hand

[`FastPathTail`](src/test/java/com/argus/feedgateway/tools/FastPathTail.java) registers with the gateway's control endpoint over UDP and prints every message, like the model engine will. In a second terminal, while the gateway runs:

```bash
MAVEN_OPTS="--add-exports java.base/jdk.internal.misc=ALL-UNNAMED" ./mvnw -pl services/feed-gateway exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.argus.feedgateway.tools.FastPathTail
```

Trades and top-of-book updates of BTCUSDT should scroll by, with consecutive sequences. `http://localhost:8081/actuator/metrics/argus.feed.messages.published` counts them.

### JVM flags

Agrona needs `--add-exports java.base/jdk.internal.misc=ALL-UNNAMED` on Java 25:

- `spring-boot:run` passes it (`agrona.jvm.args` in the parent POM).
- `java -jar target/argus-feed-gateway-*.jar` needs nothing: the jar's manifest declares `Add-Exports: java.base/jdk.internal.misc`.
- Any other launch (an IDE run configuration, a container entry point) must pass the flag itself.
