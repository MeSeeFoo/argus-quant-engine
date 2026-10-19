# 0010. Spring confined to the edges of the engine

- **Status:** Accepted
- **Date:** 2026-10-13
- **Issues:** #3, #11

## Context

Learning Spring Boot 4 is one of the goals of the project, and Spring brings configuration, lifecycle, health checks, metrics and Kafka and REST adapters for free. On the other hand, a low-latency hot path must avoid proxies, reflection and allocations, and a real ultra-low-latency system would probably not use Spring at all.

## Decision

- Every service uses Spring Boot.
- **In the model engine and the feed gateway, Spring only wires and starts the agents** (Agrona `AgentRunner`). The hot path runs as plain Java on dedicated threads, with no beans, proxies or reflection involved.
- Spring stays at the edges: configuration, Actuator, Micrometer, and the Kafka and REST adapters.
- [0009](0009-hexagonal-architecture-enforced-with-archunit.md) enforces the separation: `domain` cannot depend on Spring.

## Consequences

- One framework and one way of configuring and observing every service.
- Startup time and memory are higher than without Spring; irrelevant for long-running services.
- The decision is a learning trade-off and is stated as such in the README.

## Alternatives considered

- **No Spring in the engine.** Purer, but loses the learning goal and duplicates configuration and health checks.
- **Quarkus or Micronaut.** Less reflection and faster startup, but not the learning goal.
