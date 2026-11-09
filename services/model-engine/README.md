# argus-model-engine

Runs model deployments on live market data and publishes their outputs on the fast path (Aeron) and the durable path (Kafka) ([ADR 0014](../../docs/adr/0014-one-model-engine-for-alert-rules-and-indicators.md)).

So far it is only the service's skeleton: it starts its agent and an Aeron driver, and has no work to do. Receiving market data, models and outputs arrive with the rest of story [2.3](https://github.com/MeSeeFoo/argus-quant-engine/issues/104).

## Structure

Ports and adapters as laid out in [ADR 0009](../../docs/adr/0009-hexagonal-architecture-enforced-with-archunit.md), checked by [`ArchitectureTest`](src/test/java/com/argus/modelengine/ArchitectureTest.java):

```text
com.argus.modelengine
├── domain                   Windows and evaluation of models (empty so far)
├── application              ModelEngineAgent (the thread that runs the engine's work)
│   ├── port.in              Where data enters (empty so far)
│   └── port.out             Where data leaves (empty so far)
├── adapter
│   ├── in.<technology>      Inbound adapters, each with its configuration (none so far)
│   └── out.<technology>     Outbound adapters, each with its configuration (none so far)
└── config                   The agent, and the JVM's Media Driver and Aeron client
```

Spring builds the objects and starts the agent on its own thread through [`argus-agent-lifecycle`](../../libs/agent-lifecycle/README.md); it is never on the path of a message ([ADR 0010](../../docs/adr/0010-spring-confined-to-the-edges-of-the-engine.md)).

**Why the Aeron driver and client are in `config`.** There is one embedded Media Driver per JVM ([ADR 0021](../../docs/adr/0021-run-everything-on-fargate-with-aeron-over-udp.md)), and the engine uses Aeron in both directions: market data comes in and outputs go out. Adapters never depend on each other, so neither can own what both need. Each adapter will own its channels and streams, and only inject the client. The driver's threads back off when idle instead of spinning, as in the gateway.

## Running it next to the gateway

Start the [feed gateway](../feed-gateway/README.md#running-it) as its README says, then, in a second terminal:

```bash
./mvnw -pl services/model-engine -am install -DskipTests
./mvnw -pl services/model-engine spring-boot:run
```

Actuator listens on port **8082** (8080 is Apicurio in the [local environment](../../README.md#-local-environment) and 8081 the gateway): `http://localhost:8082/actuator/health`. Each JVM embeds its own driver in a directory of its own, so both run on one machine without sharing anything.

### JVM flags

Agrona needs `--add-exports java.base/jdk.internal.misc=ALL-UNNAMED` on Java 25:

- `spring-boot:run` passes it (`agrona.jvm.args` in the parent POM).
- `java -jar target/argus-model-engine-*.jar` needs nothing: the jar's manifest declares `Add-Exports: java.base/jdk.internal.misc`.
- Any other launch (an IDE run configuration, a container entry point) must pass the flag itself.
