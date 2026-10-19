# 0008. Apicurio as schema registry in every environment

- **Status:** Accepted
- **Date:** 2026-10-14
- **Issues:** #5, #6, #11

## Context

Kafka messages are Protobuf ([0003](0003-dual-path-streaming.md)). A schema registry lets consumers find the schema of each topic and lets producers check compatibility before publishing. The same serializers should work locally and on AWS.

## Decision

**Apicurio Registry** in every environment: in Docker Compose locally (storing its data in PostgreSQL) and on Fargate on AWS. Each topic is registered as its own artifact; Protobuf files are self-contained so that no schema references are needed.

## Consequences

- Identical client configuration and serializers locally and in the cloud.
- One more service to run on AWS instead of a managed one.
- Breaking changes are also caught before merge by `buf breaking` in CI, independently of the registry.

## Alternatives considered

- **AWS Glue Schema Registry.** Managed, but AWS-only: local development would need a different registry and different serializers.
- **Confluent Schema Registry.** The most widespread and a valid option. Apicurio was preferred because it is Apache 2.0 licensed (Confluent's registry uses the Confluent Community License) and runs the same way in both environments.
