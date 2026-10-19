# 0002. Maven multi-module build on Java 25

- **Status:** Accepted
- **Date:** 2026-10-14
- **Issues:** #1, #11

## Context

Argus has several services and shared libraries (contracts, architecture rules, model runtime) that must be built, versioned and tested together. Code is generated at build time (SBE codecs, Protobuf classes). The project is also a way to practise the tools used in the industry the author works in.

## Decision

- **Maven**, with one parent POM that declares every dependency and plugin version. Modules declare dependencies without versions. The Maven Wrapper pins the Maven version.
- **Java 25 LTS** (Eclipse Temurin; Oracle GraalVM 25 for the model engine, see [0011](0011-user-models-in-python-on-graalpy.md)) and **Spring Boot 4**.
- The Enforcer plugin fails the build on Java older than 25 or Maven older than 3.9.
- Quality gates run inside `./mvnw verify`: Spotless (google-java-format), Error Prone with NullAway, JaCoCo thresholds and ArchUnit.

## Consequences

- One command builds and checks everything, the same way locally and in CI.
- Versions live in a single place, which Dependabot updates.
- Java 25 needs a few JVM flags for libraries that use JDK internals (Agrona, Error Prone); the build passes them, services must pass them at runtime.
- Maven is more verbose than Gradle and its builds are less incremental.

## Alternatives considered

- **Gradle.** More flexible and faster incremental builds, but the author has more experience with Maven, and Maven is still the norm in banking. The gain does not justify the switch.
- **Java 21 LTS.** Better library support today, but Java 25 is the current LTS and the project is meant to last.
- **One repository per service.** Atomic changes across contracts and services would need coordinated releases.
