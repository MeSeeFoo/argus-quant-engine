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
