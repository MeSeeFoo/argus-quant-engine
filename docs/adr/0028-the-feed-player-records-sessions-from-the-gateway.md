# 0028. The feed player records sessions from the gateway

- **Status:** Accepted
- **Date:** 2026-10-29
- **Issues:** #99
- **Amends:** [0024](0024-a-feed-player-for-replay-and-backtesting.md)

## Context

[0024](0024-a-feed-player-for-replay-and-backtesting.md) creates `argus-feed-player` to replay recorded sessions, so that the feed gateway only handles live feeds. It says what the player reads (one canonical format of our own, Parquet with the fields of the SBE messages) but not where recordings come from, and it puts the player in Phase 3.

Phase 2 needs recording and replay already: the end-to-end test in CI must run without Binance or an API key. Story #65 planned it inside the gateway, recording raw Binance frames and replaying them through the gateway's decoding code, which is the replay mode that 0024 rejected.

Everything a recording needs is already on the gateway's Aeron stream: the canonical `Trade` and `BestBidAsk` messages ([0004](0004-canonical-message-contracts.md)), which any number of subscribers can receive ([0021](0021-run-everything-on-fargate-with-aeron-over-udp.md)). The passive of a shard subscribes to the same stream ([0022](0022-feed-gateway-shards-with-an-active-and-a-passive.md)).

## Decision

- The **feed player records** a session by **subscribing to the gateway's Aeron stream** of a shard, like any other consumer. The gateway knows nothing about recording and gains no code for it.
- Recording never slows the gateway. The subscriber copies each message into a pre-allocated ring buffer and a separate agent writes the file. If the player falls behind, Aeron leaves it behind instead of holding back the publisher; the gap shows in `tradeId` and `sequence`.
- The **recording format** is a **binary log of SBE messages**, not Parquet: a file header (format version, schema id and version) followed by length-prefixed records, each with the time the player received the message (ns) and the SBE message exactly as it arrived. It is documented in the player's README.
- This log is the **one canonical format** of 0024. The converters from Binance's public historical data and from QuestDB exports write it too, so the player reads a single format.
- On **replay**, the player publishes the recorded messages with the same SBE codecs, paced by the recorded receive times (real time or ×N) or as fast as possible. It stamps `receiveTimestamp` when it publishes, and keeps or shifts the other timestamps according to its mode (backtest or simulated live, as in 0024).
- The **whole feed player is built in Phase 2**: recording, replay, both modes and the converters. The end-to-end test in CI replays a synthetic recording, written by the tests with the SBE codecs; no real market data is committed.
- The gateway's decoding of Binance frames is tested in the gateway itself, with synthetic frames encoded with the Binance codecs.

## Consequences

- The gateway stays a live-only service, and the player is the only place that knows about files.
- A replay publishes exactly the bytes that were recorded, so a replayed session reaches the engine as it did live. A recording made with an older schema version is still readable, because SBE evolves compatibly ([`contracts/README.md`](../../contracts/README.md)).
- Recordings keep every top-of-book update, which the archive no longer does ([0027](0027-the-feed-gateway-publishes-market-data-on-both-paths.md)). A session converted from a QuestDB export only has the conflated quotes.
- A replay does not exercise the Binance decoding. A bug in it is caught by the gateway's tests, not by replaying a recording.
- Nothing can be recorded while the gateway is down, and a session recorded by a player that fell behind has gaps.
- The log is our own format, read only through our code. Tools such as pandas cannot read it directly; the quant lab reads QuestDB.
- Phase 2 gains a service. Phase 3 no longer has to build it.

## Alternatives considered

- **Record and replay inside the gateway** (story #65 as first written). Replay would go through the same decoding code, but it puts replay in the live service, which 0024 rejected.
- **Record raw Binance frames.** Exercises the decoding on replay, but ties every recording to one venue's wire format, and the converters and recorded instruments from other venues could not share it.
- **Parquet, as 0024 said.** Readable by analysis tools, but it brings `parquet-java` and Hadoop dependencies into Phase 2, and every message would be mapped field by field on the way in and out.
- **Aeron Archive.** Records and replays an Aeron stream with no format of our own, but its files are internal to Aeron, and the converters could not write them.
