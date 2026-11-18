# 0031. The engine detects lost market data from the gateway's sequence only

- **Status:** Accepted
- **Date:** 2026-11-18
- **Issues:** #111, #74
- **Amends:** [0022](0022-feed-gateway-shards-with-an-active-and-a-passive.md)

## Context

[0022](0022-feed-gateway-shards-with-an-active-and-a-passive.md) gives the engine a safety net: it drops any trade whose `tradeId` is not newer than the last one of its instrument. It adds that, since Binance trade ids are consecutive per symbol, the engine also detects gaps from them.

Building the engine's checks (#111) showed what that second part costs. The engine receives canonical messages ([0004](0004-canonical-message-contracts.md)), which are meant to be the same whatever the venue. Consecutive trade ids are a property of Binance, not of market data: other venues number their trades in other ways, and even Binance's `bookUpdateId` is not consecutive. A gap check on `tradeId` in the engine would have to know which venue each instrument comes from and how it numbers its trades, so the engine would be coupled to each feed it ever reads.

The gateway is where each venue is known: each venue has its own inbound adapter. A gap check against Binance's ids belongs to the Binance adapter, and it is planned there (#74).

## Decision

- **The engine detects lost messages only from the gateway's `sequence`**, within one publisher session. A new session (a restart, a failover) starts afresh and is not a gap.
- **It still drops events whose id is not newer** than the last one of their instrument: trades by `tradeId` and top-of-book updates by `bookUpdateId`. That needs ids that grow per instrument, not consecutive ones, and it is what protects the data while two gateways overlap.
- **Gaps against a venue's own ids are detected by that venue's inbound adapter in the gateway** (#74), which is the only code that knows whether its ids are consecutive.

## Consequences

- The engine stays independent of the venues: reading a new venue needs no change in it.
- Each kind of loss is detected where it can be seen:
  - between the venue and the gateway, by the venue's adapter (#74);
  - between the gateway and the engine, within a session, by the engine's `sequence` check.
- **Accepted on purpose:** the engine does not see a loss across a change of publisher session. For example, if the passive takes over and its buffer does not reach back to the active's last trade. The passive is the one that can tell: it knows the last `tradeId` the active published ([0022](0022-feed-gateway-shards-with-an-active-and-a-passive.md)) and what its buffer holds. Checking that is part of building 0022's failover.
- We would revisit this if the engine ever needs to see such losses itself: for example with a `sequence` per shard that the new active continues, rather than one per session.

## Alternatives considered

- **Detect gaps from `tradeId` in the engine, as 0022 said.** It sees losses across a failover, but it couples the engine to the venues that number trades consecutively, with a setting per venue.
- **A `sequence` per shard that survives a failover.** The passive already knows the active's last `sequence`, so it could continue it, and the engine's check would span the switch without knowing any venue. It changes what a publisher session means for the gateway and the feed player ([0028](0028-the-feed-player-records-sessions-from-the-gateway.md)), so it is left for when 0022's failover is built.
