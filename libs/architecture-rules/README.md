# argus-architecture-rules

ArchUnit rules for the [hexagonal architecture](../../README.md#-hexagonal-architecture) that every Argus service follows. The rules are enforced by tests, not by convention, and they are what keeps the signal engine's hot path free of framework code.

## Rules

Layers are recognised by package name anywhere below the service root (`com.argus.<service>`): `domain`, `application`, `adapter.in`, `adapter.out` and `config`.

| Rule | What it checks |
| :--- | :--- |
| `LAYERS_MUST_RESPECT_THE_HEXAGON` | ArchUnit's built-in [`onionArchitecture()`](https://www.archunit.org/userguide/html/000_Index.html#_onion_architecture), which is the hexagonal architecture under another name. `domain` depends on no other layer, and `application` only on `domain`. `adapter.in` and `adapter.out` never depend on each other. Outside the layers, only `config` may use them. |
| `DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS` | `domain` does not use Spring, Kafka or Aeron. The onion rule only checks dependencies between layers, not on libraries. |
| `INNER_LAYERS_MUST_NOT_DEPEND_ON_CONFIG` | `domain` and `application` do not use `config`. The onion rule misses this because `config` belongs to no layer, and `config` is Spring wiring. |
| `PACKAGES_MUST_BE_FREE_OF_CYCLES` | Every package under `com.argus` is a slice, and slices must be acyclic. |

Only what ArchUnit does not already provide is written by hand.

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

Each package under [`src/test/java/com/argus/architecture/fixtures`](src/test/java/com/argus/architecture/fixtures) is a tiny service. `compliant` follows every rule; every other fixture breaks one on purpose (`domainusesspring`, `applicationusesadapter`, `cycle`…). [`HexagonalArchitectureTest`](src/test/java/com/argus/architecture/HexagonalArchitectureTest.java) checks that each fixture violates exactly the rules it is meant to, that the failure report names the forbidden dependency, and that every rule is proven by at least one fixture. The onion rule covers several cases, so the report check is what tells them apart. When you add a rule, add a fixture that breaks it.
