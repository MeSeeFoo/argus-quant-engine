# argus-contracts

Single source of truth for every message contract: SBE schemas for the fast path (Aeron) and Protobuf schemas for the durable path (Kafka).

The Protobuf schemas are added in [#5](https://github.com/MeSeeFoo/argus-quant-engine/issues/5).

## Fast path (SBE)

The schema lives in [`src/main/resources/sbe/argus-fastpath.xml`](src/main/resources/sbe/argus-fastpath.xml). `sbe-tool` generates the Java codecs during `generate-sources` into `target/generated-sources/sbe`, package `com.argus.contracts.sbe`. Generated sources are never committed.

| Property | Value |
| :--- | :--- |
| Schema id | `1` |
| Schema version | `0` |
| Byte order | Little endian |

These are **our own canonical contracts**, not the exchange's. The feed gateway may read Binance's SBE streams, but it maps them to these messages. Binance's codecs live in the gateway, so a change on the exchange side never reaches the rest of the system.

### Messages

| templateId | Message | Meaning | Route |
| :--- | :--- | :--- | :--- |
| 1 | `Trade` | An execution that happened on the venue: a buyer and a seller crossed at a price and quantity. It is a fact and is never corrected. | feed-gateway → signal-engine |
| 2 | `BestBidAsk` | The current top of book: the best price someone is willing to buy at (bid) and sell at (ask), with their quantities. It is intent, not execution; each update replaces the previous one. | feed-gateway → signal-engine |
| 10 | `Signal` | A rule's conclusion: be LONG, SHORT or FLAT on an instrument, with a strength. A recommendation, not an order. It points to the market event that triggered it. | signal-engine → fast-path consumers |
| 99 | `Heartbeat` | "Still alive" on an idle stream. Carries the last published sequence so receivers detect gaps even when nothing else arrives. | every fast-path stream |

Volume is not a message of its own: a trade's `quantity` is its volume, and aggregated volume (candles, windows) is computed by the signal engine and published to Kafka.

### Common types

| Type | Encoding | Notes |
| :--- | :--- | :--- |
| `Decimal64` | `int64` mantissa, **constant** exponent `-8` | `value = mantissa × 10⁻⁸`. The exponent takes no bytes on the wire. Covers every Binance tick size; the maximum is about 9.2 × 10¹⁰. |
| `EpochNanos` | `int64` | Nanoseconds since the Unix epoch, UTC. |
| `Sequence` | `uint64` | Monotonic per publisher, starts at 1. A jump means messages were lost. |
| `Symbol` | `char[24]`, US-ASCII | As the venue names it, uppercase, no separator, null-padded (`BTCUSDT`). |
| `Venue` | `char[12]`, US-ASCII | Uppercase venue code, null-padded (`BINANCE`). |
| `Side` | `uint8` enum | Aggressor side of a trade: `BUY`, `SELL`. |
| `Direction` | `uint8` enum | `FLAT`, `LONG`, `SHORT`. |

Symbol and venue are strings so that users can refer to instruments directly in rules and indicators (`"BINANCE:BTCUSDT"`), without an id catalogue. They are fixed-length, so messages keep a fixed size and can be read without allocating: compare the bytes instead of building a `String`. A value that does not fit is rejected (`IndexOutOfBoundsException`), never truncated.

### Fields

Fields are ordered from 8 to 1 bytes, with the char arrays last, so every 64-bit field stays 8-byte aligned. Offsets are relative to the end of the 8-byte message header.

**`Trade`** (85 bytes)

| Offset | Field | Type | Meaning |
| --: | :--- | :--- | :--- |
| 0 | `sequence` | `Sequence` | Gateway sequence, shared with `BestBidAsk` |
| 8 | `tradeId` | `int64` | Trade id on the venue |
| 16 | `price` | `Decimal64` | Execution price |
| 24 | `quantity` | `Decimal64` | Executed quantity |
| 32 | `exchangeTimestamp` | `EpochNanos` | Execution time on the venue |
| 40 | `receiveTimestamp` | `EpochNanos` | Time the gateway received it |
| 48 | `aggressorSide` | `Side` | Side that crossed the spread |
| 49 | `venue` | `Venue` | |
| 61 | `symbol` | `Symbol` | |

**`BestBidAsk`** (100 bytes)

| Offset | Field | Type | Meaning |
| --: | :--- | :--- | :--- |
| 0 | `sequence` | `Sequence` | Gateway sequence, shared with `Trade` |
| 8 | `bookUpdateId` | `int64` | Book version on the venue; updates not newer than the last one are dropped |
| 16 | `bidPrice` | `Decimal64` | Best bid |
| 24 | `bidQuantity` | `Decimal64` | Quantity at the best bid |
| 32 | `askPrice` | `Decimal64` | Best ask |
| 40 | `askQuantity` | `Decimal64` | Quantity at the best ask |
| 48 | `exchangeTimestamp` | `EpochNanos` | Event time on the venue |
| 56 | `receiveTimestamp` | `EpochNanos` | Time the gateway received it |
| 64 | `venue` | `Venue` | |
| 76 | `symbol` | `Symbol` | |

Mid price and spread are derived values and are computed by the engine.

**`Signal`** (91 bytes)

| Offset | Field | Type | Meaning |
| --: | :--- | :--- | :--- |
| 0 | `signalId` | `Sequence` | Monotonic per engine; consumers use it for gap detection |
| 8 | `triggerSequence` | `Sequence` | `sequence` of the market event that triggered the rule |
| 16 | `strength` | `Decimal64` | Confidence in [0, 1]; the direction is a separate field |
| 24 | `referencePrice` | `Decimal64` | Price the rule looked at (usually last trade or mid) |
| 32 | `triggerExchangeTimestamp` | `EpochNanos` | `exchangeTimestamp` of the triggering event |
| 40 | `emitTimestamp` | `EpochNanos` | Time the engine published the signal |
| 48 | `ruleId` | `uint32` | Rule id |
| 52 | `ruleVersion` | `uint16` | Rule version that produced the signal |
| 54 | `direction` | `Direction` | Recommended position |
| 55 | `venue` | `Venue` | |
| 67 | `symbol` | `Symbol` | |

**`Heartbeat`** (16 bytes)

| Offset | Field | Type | Meaning |
| --: | :--- | :--- | :--- |
| 0 | `sequence` | `Sequence` | Last sequence published on the stream |
| 8 | `timestamp` | `EpochNanos` | Sender clock |

The timestamps measure latency along the path: `exchangeTimestamp → receiveTimestamp` is the network hop, `receiveTimestamp → emitTimestamp` is the time spent inside the pipeline.

Every field is mandatory in version 0: no field uses its null value.

### Versioning

SBE decoders read old and new messages as long as the change is additive. The rules:

1. **Add a field:** append it at the end of the message with `sinceVersion="N"` and bump the schema `version` to `N`. Older decoders skip it thanks to `blockLength`; newer decoders see the null value when reading an older message.
2. **Add a message:** give it a new `templateId`. Consumers ignore template ids they do not know.
3. **Breaking change** (removing, renaming the type of, or reordering a field, or changing a `Decimal64` exponent): create a new schema `id` and run both schemas side by side during the migration.

[`SchemaTest`](src/test/java/com/argus/contracts/sbe/SchemaTest.java) pins the schema id, version, template ids and block lengths. If it fails, the wire contract changed: check these rules before updating the expected values.

### Usage

The codecs depend on Agrona. On Java 25, any JVM that loads them must start with:

```
--add-exports java.base/jdk.internal.misc=ALL-UNNAMED
```

The parent POM passes it to Surefire and Failsafe through the `agrona.jvm.args` property.
