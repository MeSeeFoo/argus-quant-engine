# 0009. Hexagonal architecture enforced with ArchUnit

- **Status:** Accepted
- **Date:** 2026-10-15
- **Issues:** #8, #11

## Context

Each service has domain logic (windows, indicators, rule evaluation, routing of notifications) and adapters to Aeron, Kafka, databases and HTTP. In the engine, framework code on the hot path would add allocation and indirection. Conventions written in a README tend to erode, especially when part of the code is written by AI agents.

## Decision

Every service follows the same hexagonal structure (`domain`, `application`, `adapter.in`, `adapter.out`, `config`), and the rules are **tests**, not conventions: [`libs/architecture-rules`](../../libs/architecture-rules/README.md) provides ArchUnit rules that each service applies with a single test class. Among them: `domain` does not depend on Spring, Kafka or Aeron, adapters do not depend on each other, and packages are free of cycles.

## Consequences

- A forbidden dependency fails the build, for people and agents alike.
- The domain can be tested without Spring, brokers or databases.
- Some boilerplate (ports and adapters) even for simple use cases.

## Alternatives considered

- **Conventions only.** They erode.
- **Java modules (JPMS) per layer.** Stronger, but heavy for packages inside one service, and awkward with Spring.
- **Layered architecture.** Simpler, but lets the domain depend on persistence.
