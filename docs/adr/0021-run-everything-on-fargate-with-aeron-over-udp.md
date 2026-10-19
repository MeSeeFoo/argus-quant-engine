# 0021. Run everything on Fargate, with Aeron over UDP

- **Status:** Accepted
- **Date:** 2026-10-19
- **Issues:** #11, #43
- **Amends:** [0003](0003-dual-path-streaming.md)

## Context

The original AWS design ran the feed gateway, the engine and an Aeron Media Driver sidecar in the same ECS task on EC2, sharing IPC (`ipcMode: task`, not available on Fargate), and everything else on Fargate. That meant two capacity types, EC2 instances to manage, and a deployment shape specific to the fast path.

In the review we agreed that Argus is **low latency, not ultra-low latency**: Python models and a feed that crosses the public internet put the end-to-end budget in milliseconds. Aeron IPC would save microseconds nobody would notice, and the effort is better spent on the code.

## Decision

- **Every service runs on Fargate.** No EC2 capacity provider.
- **Aeron over UDP unicast everywhere**, with an **embedded Media Driver** in each JVM (there is no shared memory between tasks). IPC remains possible: it is only a different channel URI in the configuration, so no code depends on the choice.
- **Discovery:** subscribers register with the publisher's control endpoint (dynamic multi-destination-cast), and publishers are found through **AWS Cloud Map** DNS names. VPCs have no multicast.
- **Idle strategies with back-off** instead of busy spinning: Fargate does not allow pinning threads to cores, and a vCPU is a shared hyperthread, so spinning burns paid CPU without guaranteeing latency.
- **Latency targets:** microseconds are kept as a goal **inside the model engine** (decode → Java indicators → Python model). Between tasks, latency is measured and published with the environment it was measured in, not promised.

## Consequences

- One capacity type, simpler Terraform, and every service deployed the same way.
- Aeron's files go to the task's ephemeral storage, since Fargate does not support custom `/dev/shm` sizes or tmpfs mounts; more jitter than a tuned host, accepted.
- Every hop on the fast path crosses the network: the hop between gateway and engine is now tens to hundreds of microseconds instead of a few.
- This rules out shared memory between gateway instances for high availability ([0022](0022-feed-gateway-shards-with-an-active-and-a-passive.md)).
- Clock synchronisation on Fargate is NTP-based (Amazon Time Sync), with millisecond-level accuracy: relevant for [0026](0026-regulatory-archive-for-mifid-ii.md).

## Alternatives considered

- **ECS on EC2 for the fast path, with Aeron IPC (the original design).** Lower latency on the gateway → engine hop, at the cost of a second capacity type and host management, for a gain the Python models hide.
- **Drop Aeron, use Kafka for everything.** Simpler, but loses the low-latency design that is one of the project's goals, and the fast path to trading consumers.
- **gRPC streaming.** Familiar, but allocation-heavy and with more jitter than Aeron.
