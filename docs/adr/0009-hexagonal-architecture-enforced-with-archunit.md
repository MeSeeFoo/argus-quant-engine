# 0009. Hexagonal architecture enforced with ArchUnit

- **Status:** Accepted
- **Date:** 2026-10-15, revised 2026-10-21 (see [Revisions](#revisions))
- **Issues:** #8, #11, #82

## Context

Each service has domain logic (windows, indicators, rule evaluation, routing of notifications) and adapters to Aeron, Kafka, databases and HTTP. In the engine, framework code on the hot path would add allocation and indirection. Conventions written in a README tend to erode, especially when part of the code is written by AI agents.

Hexagonal architecture (ports and adapters) has several common package layouts that apply the same rule (dependencies point towards the domain) with different names. The layout has to be one and the same in every service, so that people and agents find things in the same place.

## Decision

Every service follows **ports and adapters** as laid out by Tom Hombergs' [*buckpal*](https://github.com/thombergs/buckpal) (*Get Your Hands Dirty on Clean Architecture*), and the rules are **tests**, not conventions: [`libs/architecture-rules`](../../libs/architecture-rules/README.md) provides ArchUnit rules that each service applies with a single test class.

### Layout

```text
com.argus.<service>
├── domain                   Pure Java: entities, value objects, domain rules
├── application              Use cases: the services, and the agents that run them
│   ├── port.in              Where data enters the service (implemented by application, or by an inbound adapter it polls)
│   └── port.out             Where data leaves the service (implemented by outbound adapters)
├── adapter
│   ├── in.<technology>      Brings data in through port.in: WebSocket, Kafka consumer, REST…
│   └── out.<technology>     Takes data out through port.out: Aeron publisher, Kafka producer, database…
└── config                   What is generic to the service, and starting it
```

### Rules

- **Dependencies point inwards.** `domain` depends on no other layer, `application` only on `domain`, and adapters never on each other.
- **`domain` uses no Spring, Kafka or Aeron.** Libraries that keep the hot path allocation-free, such as Agrona, are allowed.
- **Every port is an interface in `application.port.in` or `application.port.out`.** There are no interfaces elsewhere in `application`.
- **Adapters see the application only through its ports**, plus the `domain` types the ports carry. They never use a service directly.
- **A port's direction follows the data, not who calls whom.** `port.in` holds every port through which data enters the service, whether the application implements it (the gateway's `IngestMarketData`) or an inbound adapter implements it and the application polls it (for example, a source that an agent polls). `port.out` holds every port through which data leaves (the gateway's `MarketDataPublisher`). Calling direction would put a polled source in `port.out`, next to the publishers, and hide where data comes in.
- **Each adapter uses only the ports of its direction.** `adapter.in` never uses `port.out`, and `adapter.out` never uses `port.in`.
- **Each adapter owns its configuration.** Its properties (for example `argus.feed-gateway.binance.*`) and the Spring configuration that builds it live in its own package. `config` holds only what belongs to the service as a whole, such as the instruments it handles, the use cases and the agents that run them, and never knows an adapter's details.
- **An agent may be a use case.** In the services built on Agrona agents ([0010](0010-spring-confined-to-the-edges-of-the-engine.md)), the agent that runs the service's work on its own thread is the use case, and lives in `application`. Spring only starts it, from `config`.
- **Packages are free of cycles.**

## Consequences

- A forbidden dependency fails the build, for people and agents alike.
- The domain and the use cases can be tested without Spring, brokers or databases.
- Port packages make each service's boundary visible at a glance: where data comes in (`port.in`) and where it goes out (`port.out`).
- Some boilerplate (ports, a configuration per adapter) even for simple use cases.
- Replacing an adapter (another venue, a replay instead of a live feed) touches its own package, nothing else. When two adapters provide the same port, the choice lives in the adapters themselves (each configuration is conditional on a property), not in `config`.

## Alternatives considered

- **Conventions only.** They erode.
- **Java modules (JPMS) per layer.** Stronger, but heavy for packages inside one service, and awkward with Spring.
- **Layered architecture.** Simpler, but lets the domain depend on persistence.
- **Layered DDD layout** (`domain`, `application`, `infrastructure`, `interfaces`, as in Evans' [dddsample](https://github.com/citerus/dddsample-core)). The same dependency rule with other names, but `infrastructure` mixes inbound and outbound concerns, and ports have no fixed home.
- **Hybrid layout** (`domain`, `application`, `infrastructure` with the adapters inside, common in Spain through CodelyTV). Widespread, but direction (in or out) is not visible in the packages, and it would leave the ArchUnit rules with less to check.

## Revisions

This ADR was refined in place while its first implementation (the feed gateway, Phase 2) settled the concepts. See [Refining an accepted ADR](README.md#refining-an-accepted-adr).

| Date | Issue | Change |
| :--- | :--- | :--- |
| 2026-10-20 | #82 | The layout chosen among the hexagonal variants (buckpal), the mandatory `port.in` and `port.out` packages and their ArchUnit rules, adapters seeing the application only through ports, adapter-owned configuration, and agents as use cases. |
| 2026-10-21 | #82 | A port's direction follows the data, not who calls whom, so a polled source is an input port; each adapter uses only the ports of its direction. Choosing between two adapters of one port happens in the adapters, conditional on a property, not in `config`. In the gateway, the queue between the connection threads and the agent belongs to the application: the inbound adapter decodes and calls `IngestMarketData`, so the polled `MarketDataSource` port is gone. |
