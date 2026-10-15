# argus-architecture-rules

ArchUnit rules for the [hexagonal architecture](../../README.md#-hexagonal-architecture) that every Argus service follows. The rules are enforced by tests, not by convention, and they are what keeps the signal engine's hot path free of framework code.

## Rules

Layers are recognised by package name anywhere below the service root (`com.argus.<service>`): `domain`, `application`, `adapter.in`, `adapter.out` and `config`.

| Rule | Why |
| :--- | :--- |
| `domain` must not depend on Spring, Kafka or Aeron | The domain is pure Java. |
| `domain` must not depend on `application`, `adapter` or `config` | The domain is the centre of the hexagon: every other layer depends on it, never the other way round. |
| `application` must not depend on `adapter` or `config` | Use cases reach the outside world only through their ports. |
| Only `config` may depend on `adapter` | Adapters are plugged into ports by the wiring, never referenced directly. |
| `adapter.in` and `adapter.out` must not depend on each other | An inbound adapter drives a use case; outbound adapters are reached through ports. |
| No dependency cycles between packages | Every package under `com.argus` is a slice, and slices must be acyclic. |

A layer that a service does not have yet is not an error, so the rules can be applied from the first commit.

## Using it in a new service

1. Add the dependency with test scope. Its version is managed by the parent POM:

   ```xml
   <dependency>
       <groupId>com.argus</groupId>
       <artifactId>argus-architecture-rules</artifactId>
       <scope>test</scope>
   </dependency>
   ```

   It brings ArchUnit and its JUnit engine along, so nothing else is needed.

2. Add a single test class at the root of the service's test sources:

   ```java
   package com.argus.feedgateway;

   import com.argus.architecture.HexagonalArchitecture;
   import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
   import com.tngtech.archunit.junit.AnalyzeClasses;
   import com.tngtech.archunit.junit.ArchTest;
   import com.tngtech.archunit.junit.ArchTests;

   @AnalyzeClasses(packages = "com.argus.feedgateway", importOptions = DoNotIncludeTests.class)
   class ArchitectureTest {

     @ArchTest static final ArchTests hexagonal = ArchTests.in(HexagonalArchitecture.class);
   }
   ```

   `DoNotIncludeTests` keeps test code out of the analysis: tests may legitimately wire adapters by hand.

The rules then run with the rest of the tests in `./mvnw verify`. Each one is reported as its own test, named after the rule constant in [`HexagonalArchitecture`](src/main/java/com/argus/architecture/HexagonalArchitecture.java).

## How the rules are tested

Each package under [`src/test/java/com/argus/architecture/fixtures`](src/test/java/com/argus/architecture/fixtures) is a tiny service. `compliant` follows every rule; every other fixture breaks one on purpose (`domainusesspring`, `applicationusesadapter`, `cycle`…). [`HexagonalArchitectureTest`](src/test/java/com/argus/architecture/HexagonalArchitectureTest.java) checks that each fixture violates exactly the rules it is meant to, and that every rule is proven by at least one fixture. When you add a rule, add a fixture that breaks it.
