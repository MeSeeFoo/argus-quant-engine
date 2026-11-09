# argus-agent-lifecycle

Runs an Agrona agent on its own thread while the Spring context is running. Spring starts and stops the agent and is never on the path of a message ([ADR 0010](../../docs/adr/0010-spring-confined-to-the-edges-of-the-engine.md)).

It is for the services built on agents: the feed gateway and the model engine. It holds this one class on purpose: it is not a place for general helpers.

## Using it

Add the dependency, whose version is managed by the parent POM:

```xml
<dependency>
    <groupId>com.argus</groupId>
    <artifactId>argus-agent-lifecycle</artifactId>
</dependency>
```

Declare one bean per agent:

```java
@Bean
AgentLifecycle feedAgent(MarketDataQueue queue, MarketDataRelay relay) {
  return new AgentLifecycle(new FeedAgent(queue, relay, ...), new BackoffIdleStrategy());
}
```

- **Thread.** The agent's thread is named after its `roleName()`.
- **Failures.** An exception from `doWork` is logged and the agent keeps running.
- **Order.** An agent that others feed, such as a Kafka sender, takes a lower Spring phase through the three-argument constructor: it starts before them and stops after them, so it drains what they left.
- **JVM flags.** Agrona needs `--add-exports java.base/jdk.internal.misc=ALL-UNNAMED` on Java 25, in every JVM that loads it.
