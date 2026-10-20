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

## Running it

```bash
./mvnw -pl services/feed-gateway -am install -DskipTests
./mvnw -pl services/feed-gateway spring-boot:run
```

Actuator listens on port **8081** (8080 is Apicurio in the [local environment](../../README.md#-local-environment)): `http://localhost:8081/actuator/health`.

### JVM flags

Agrona needs `--add-exports java.base/jdk.internal.misc=ALL-UNNAMED` on Java 25:

- `spring-boot:run` passes it (`agrona.jvm.args` in the parent POM).
- `java -jar target/argus-feed-gateway-*.jar` needs nothing: the jar's manifest declares `Add-Exports: java.base/jdk.internal.misc`.
- Any other launch (an IDE run configuration, a container entry point) must pass the flag itself.
