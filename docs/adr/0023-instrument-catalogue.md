# 0023. Instrument catalogue

- **Status:** Accepted
- **Date:** 2026-10-19
- **Issues:** #11, #43

## Context

Several components need to know which instruments exist: the feed gateway (what to subscribe to, in which shard), the model engine (which Aeron stream carries an instrument), the UI (which instruments a deployment can use), and the feed player (instruments that Binance does not list). Load is very uneven: a few symbols generate most of the messages.

## Decision

- An **instrument catalogue** records, for each instrument: venue, symbol, shard, and whether it is active.
- It is managed by **operations** through the BFF (restricted role, audited), stored in PostgreSQL and distributed through the compacted topic `argus.instruments.v1` ([0012](0012-distribute-model-state-through-compacted-topics.md)).
- **Assigning instruments to shards is an operations decision**, helped by the gateway's metrics, not a hash. Criteria: message rate (the busiest symbols get shards of their own or with few companions), the venue's limits per connection, and the venue's limits **per IP**: on Fargate in private subnets every task leaves through the same NAT gateway address, so all shards, active and passive, share those limits.
- The gateway reads the instruments of its shard and subscribes or unsubscribes on its open connection when they change (Binance supports `SUBSCRIBE` and `UNSUBSCRIBE` messages): no redeploy.
- **One Aeron stream per shard.** The engine reads the catalogue to subscribe only to the shards that contain its deployments' instruments.
- Moving an instrument between shards needs no special protocol: the new shard subscribes, the old one unsubscribes, and the engine's deduplication by trade id absorbs the overlap ([0022](0022-feed-gateway-shards-with-an-active-and-a-passive.md)).
- Instruments of a multi-instrument model do not need to share a shard.

## Consequences

- No instrument is hard-coded in any service.
- Adding an instrument is a data change, audited like any other change to what enters the system.
- In Phase 2 there is a single shard and the catalogue is almost invisible; the design is ready when shards appear.

## Alternatives considered

- **`hash(symbol) mod N`.** Automatic, but ignores the very uneven load and moves most instruments when N changes.
- **Static lists per shard in Terraform.** Explicit, but adding an instrument needs a redeploy, and the list would be duplicated in the UI and the engine.
