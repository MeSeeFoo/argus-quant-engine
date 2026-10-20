# argus-feed-gateway

Receives market data from a venue and publishes it on the fast path (Aeron + SBE) in the [canonical format](../../contracts/README.md). In Phase 2 it reads BTCUSDT from Binance, with a single shard and a single task ([ADR 0022](../../docs/adr/0022-feed-gateway-shards-with-an-active-and-a-passive.md), [ADR 0023](../../docs/adr/0023-instrument-catalogue.md)).

## Structure

Hexagonal packages under `com.argus.feedgateway`, checked by [`ArchitectureTest`](src/test/java/com/argus/feedgateway/ArchitectureTest.java) ([ADR 0009](../../docs/adr/0009-hexagonal-architecture-enforced-with-archunit.md)):

| Package | Content |
| :--- | :--- |
| `domain` | Mapping of venue events to the canonical contracts. Plain Java, no allocation in steady state |
| `application` | Use cases and the ports the adapters implement |
| `adapter.in` | Market data sources: the venue's live stream, or a recorded session |
| `adapter.out` | The fast path over Aeron |
| `config` | Spring wiring |

Spring only builds the agents and starts them, each on its own thread through an Agrona `AgentRunner` ([`AgentLifecycle`](src/main/java/com/argus/feedgateway/config/AgentLifecycle.java)). The hot path never runs through beans or proxies ([ADR 0010](../../docs/adr/0010-spring-confined-to-the-edges-of-the-engine.md)).

## From Binance to canonical messages

The gateway reads Binance's [SBE market data stream](https://developers.binance.com/docs/binance-spot-api-docs/sbe-market-data-streams): the JSON `@bookTicker` stream carries no event time, which the canonical `BestBidAsk` needs ([ADR 0017](../../docs/adr/0017-multi-instrument-models-and-event-time.md)).

- **Binance's codecs stay here.** [`src/main/sbe/binance/stream_1_0.xml`](src/main/sbe/binance/stream_1_0.xml) is Binance's stream schema, copied unchanged from [binance-spot-api-docs](https://github.com/binance/binance-spot-api-docs/blob/master/sbe/schemas/stream_1_0.xml). `sbe-tool` generates its codecs into `com.argus.feedgateway.adapter.in.binance.sbe` at build time; nothing outside the gateway sees them ([ADR 0004](../../docs/adr/0004-canonical-message-contracts.md)). The schema's own warnings (a `uint32` group count) are not fatal for this schema only.
- **[`BinanceStreamDecoder`](src/main/java/com/argus/feedgateway/adapter/in/binance/BinanceStreamDecoder.java)** (`adapter.in`) decodes `TradesStreamEvent` and `BestBidAskStreamEvent` with flyweights. One trades event can carry several trades, each published on its own. Other messages, other schemas, malformed frames and symbols outside the shard are counted and ignored.
- **[`MarketDataNormaliser`](src/main/java/com/argus/feedgateway/application/MarketDataNormaliser.java)** (`application`) turns the venue's values into canonical ones and hands them to the `MarketDataPublisher` port.

| Canonical | From Binance |
| :--- | :--- |
| `Trade.exchangeTimestamp` | `transactTime` (µs → ns) |
| `BestBidAsk.exchangeTimestamp` | `eventTime` (µs → ns) |
| Prices and quantities | mantissa and exponent → `Decimal64` (exponent −8). A value with more than eight decimals or too large for `int64` rejects the whole message, never rounds it ([`Decimal64`](src/main/java/com/argus/feedgateway/domain/Decimal64.java)) |
| `aggressorSide` | `isBuyerMaker` true → `SELL`, false → `BUY` |
| `bookUpdateId` | `bookUpdateId`; an update not newer than the last one of its instrument is dropped ([`BookUpdateFilter`](src/main/java/com/argus/feedgateway/domain/BookUpdateFilter.java)) |

Everything rejected is counted in [`MarketDataCounters`](src/main/java/com/argus/feedgateway/application/MarketDataCounters.java). The decoding path allocates nothing per frame; [`BinanceStreamDecoderAllocationTest`](src/test/java/com/argus/feedgateway/adapter/in/binance/BinanceStreamDecoderAllocationTest.java) checks it with the JVM's per-thread allocation counter. Tests build synthetic frames with Binance's encoders: no real market data is committed.

## Publishing on the fast path

[`AeronMarketDataPublisher`](src/main/java/com/argus/feedgateway/adapter/out/aeron/AeronMarketDataPublisher.java) (`adapter.out`) implements the `MarketDataPublisher` port: it encodes canonical `Trade`, `BestBidAsk` and `Heartbeat` messages from [`argus-contracts`](../../contracts/README.md) and offers them to an Aeron publication.

- **Transport** ([ADR 0021](../../docs/adr/0021-run-everything-on-fargate-with-aeron-over-udp.md)):
  - an embedded Media Driver in its own directory, whose threads back off instead of spinning;
  - one `ExclusivePublication` per shard, over UDP with a dynamic multi-destination-cast control endpoint, where subscribers register. Flow control is `fc=max`: the fastest subscriber sets the pace, and a slower one loses data on its own side (it sees gaps) instead of slowing the gateway down. Those losses are the subscriber's, not counted here.
- **Sequence.** `Trade` and `BestBidAsk` share one `sequence`, starting at 1.
- **Never blocks.** When Aeron refuses a message (no subscriber, back-pressure, a closed publication), the message is dropped and counted. It keeps its sequence, so subscribers see the loss as a gap. The one retry is immediate and single, after `ADMIN_ACTION`: the offer reached the end of a term and rotated it without writing the message.
- **Heartbeat.** The publishing thread calls `heartbeatIfIdle(monotonicNow, epochNow)` on every duty cycle: the monotonic clock schedules the checks, so a wall clock stepping backwards cannot silence the heartbeat, and the epoch clock is written in the message. When nothing was published during a whole interval, it sends a `Heartbeat` with the last sequence and the sender clock. An idle stream therefore carries one every one to two intervals.
- **Threading and allocation.** Single-threaded and allocation-free, including when subscribers fall behind ([`AeronMarketDataPublisherTest`](src/test/java/com/argus/feedgateway/adapter/out/aeron/AeronMarketDataPublisherTest.java) runs a real driver and subscriber).

| Property | Default | Meaning |
| :--- | :--- | :--- |
| `argus.feed-gateway.aeron.channel` | `aeron:udp?control=localhost:40456\|control-mode=dynamic\|fc=max` | Publication channel. Subscribers use `aeron:udp?endpoint=<their host>:0\|control=<this control endpoint>\|control-mode=dynamic` |
| `argus.feed-gateway.aeron.stream-id` | `1001` | Stream of shard 1 |
| `argus.feed-gateway.aeron.heartbeat-interval` | `1s` | Idle time before a `Heartbeat` |

Metrics (`/actuator/metrics`):

| Metric | Tag | Meaning |
| :--- | :--- | :--- |
| `argus.feed.messages.published` | `type`: `trade`, `best_bid_ask`, `heartbeat` | Messages handed to Aeron |
| `argus.feed.messages.dropped` | `reason`: `not_connected`, `back_pressured`, `closed`, `other` | Messages dropped instead of blocking |

## The Binance connection and the gateway agent

[`BinanceStreamClient`](src/main/java/com/argus/feedgateway/adapter/in/binance/BinanceStreamClient.java) (`adapter.in`) opens one WebSocket, with the JDK's own client, to the combined stream of every instrument: `wss://stream-sbe.binance.com:9443/stream?streams=btcusdt@trade/btcusdt@bestBidAsk`.

- **API key.** It goes in the `X-MBX-APIKEY` header and comes from the `BINANCE_API_KEY` environment variable. It is never logged or committed, and the gateway refuses to start without it. Binance only accepts Ed25519 keys for this stream; a read-only key is enough.
- **Pings.** Binance pings every 20 seconds and closes the connection after a minute without a pong. The JDK client answers each ping with a pong carrying its payload.
- **Text frames.** Subscription responses and `serverShutdown` events are logged for now. Reconnecting is story [2.3].
- **From the connection to the agent.** The connection's callbacks run on the HTTP client's threads. They only copy each binary frame, with its receive time, into a pre-allocated ring buffer (1 MiB, about ten thousand frames), putting frames that arrive in parts back together first. When the ring is full the frame is dropped and counted: the connection never waits.

[`FeedAgent`](src/main/java/com/argus/feedgateway/application/FeedAgent.java) is the gateway's only hot-path thread. On each duty cycle it drains up to 64 frames from the ring (decode, normalise, publish on Aeron), then lets the publisher send a heartbeat if the stream was idle. Everything after the ring is single-threaded and allocation-free.

| Property | Default | Meaning |
| :--- | :--- | :--- |
| `argus.feed-gateway.binance.uri` | `wss://stream-sbe.binance.com:9443` | Base address of the stream |
| `argus.feed-gateway.binance.api-key` | `${BINANCE_API_KEY}` | Read-only Ed25519 API key |
| `argus.feed-gateway.binance.symbols` | `BTCUSDT` | The shard's instruments |

More metrics: `argus.feed.messages.rejected{reason}` (`unsupported`, `unknown_symbol`, `unrepresentable`, `stale_book`) and `argus.feed.frames.dropped`.

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
