# argus-contracts

Single source of truth for every message contract: SBE schemas for the fast path (Aeron) and Protobuf schemas for the durable path (Kafka). Both follow the same conventions: decimals as `int64` mantissas with scale 10⁻⁸, timestamps in epoch nanoseconds, venue and symbol as text.

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
| 1 | `Trade` | An execution that happened on the venue: a buyer and a seller crossed at a price and quantity. It is a fact and is never corrected. | feed-gateway, feed-player → model-engine |
| 2 | `BestBidAsk` | The current top of book: the best price someone is willing to buy at (bid) and sell at (ask), with their quantities. It is intent, not execution; each update replaces the previous one. | feed-gateway, feed-player → model-engine |
| 11 | `IndicatorValue` | The values an `Indicator` deployment computed (a VWAP, the three Bollinger bands…), for one or more instruments. | model-engine → fast-path consumers, other deployments |
| 12 | `Alert` | An alert an `AlertRule` deployment raised, with a severity and a message. | model-engine → fast-path consumers |
| 99 | `Heartbeat` | "Still alive" on an idle stream. Carries the last published sequence so receivers detect gaps even when nothing else arrives. | every fast-path stream |

Template id 10 belonged to `Signal`, removed by [ADR 0014](../docs/adr/0014-one-model-engine-for-alert-rules-and-indicators.md); it is never reused.

Volume is not a message of its own: a trade's `quantity` is its volume, and aggregated volume (candles, windows) is computed by the model engine.

### Common types

| Type | Encoding | Notes |
| :--- | :--- | :--- |
| `Decimal64` | `int64` mantissa, **constant** exponent `-8` | `value = mantissa × 10⁻⁸`. The exponent takes no bytes on the wire. Covers every Binance tick size; the maximum is about 9.2 × 10¹⁰. |
| `EpochNanos` | `int64` | Nanoseconds since the Unix epoch, UTC. |
| `Sequence` | `uint64` | Monotonic per publisher, starts at 1. A jump means messages were lost. |
| `Symbol` | `char[24]`, US-ASCII | As the venue names it, uppercase, no separator, null-padded (`BTCUSDT`). |
| `Venue` | `char[12]`, US-ASCII | Uppercase venue code, null-padded (`BINANCE`). |
| `Side` | `uint8` enum | Aggressor side of a trade: `BUY`, `SELL`. |
| `Severity` | `uint8` enum | Severity of an alert: `INFO`, `WARNING`, `CRITICAL`. |
| `groupSizeEncoding` | `uint16` block length + `uint16` count | Header of a repeating group. |
| `varStringEncoding` | `uint16` length + UTF-8 bytes | Variable-length text, after every group. |

Market data uses `Decimal64`; values computed by models (`IndicatorValue`) use `double`, because a z-score or a correlation does not fit a fixed scale of 10⁻⁸. `NaN` means the model does not have enough data yet.

Symbol and venue are strings so that users can refer to instruments directly in models (`"BINANCE:BTCUSDT"`), without an id catalogue. They are fixed-length, so messages keep a fixed size and can be read without allocating: compare the bytes instead of building a `String`. A value that does not fit is rejected (`IndexOutOfBoundsException`), never truncated.

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

**`IndicatorValue`** (44 bytes + groups)

| Offset | Field | Type | Meaning |
| --: | :--- | :--- | :--- |
| 0 | `sequence` | `Sequence` | Monotonic per publisher; consumers use it for gap detection |
| 8 | `triggerSequence` | `Sequence` | `sequence` of the input event that triggered the evaluation |
| 16 | `triggerExchangeTimestamp` | `EpochNanos` | Event time of the triggering input |
| 24 | `emitTimestamp` | `EpochNanos` | Time the engine published the value |
| 32 | `deploymentId` | `int64` | Deployment that produced the value |
| 40 | `deploymentRevision` | `uint32` | Revision of the deployment (model version and parameters) |
| group 7 | `values` | `double` per entry | One entry per output declared in `ModelConfiguration`, in order |
| group 8 | `instruments` | `venue` + `symbol` per entry | Instruments the value refers to, in slot order |

`values` comes first so that its doubles start 8-byte aligned (8 + 44 + 4 = 56); `SchemaTest` checks it.

**`Alert`** (45 bytes + group + text)

| Offset | Field | Type | Meaning |
| --: | :--- | :--- | :--- |
| 0 | `sequence` | `Sequence` | Monotonic per publisher; consumers use it for gap detection and deduplication |
| 8 | `triggerSequence` | `Sequence` | `sequence` of the input event that triggered the evaluation |
| 16 | `triggerExchangeTimestamp` | `EpochNanos` | Event time of the triggering input |
| 24 | `emitTimestamp` | `EpochNanos` | Time the engine published the alert |
| 32 | `deploymentId` | `int64` | Deployment that raised the alert |
| 40 | `deploymentRevision` | `uint32` | Revision of the deployment (model version and parameters) |
| 44 | `severity` | `Severity` | How urgent the alert is |
| group 8 | `instruments` | `venue` + `symbol` per entry | Instruments the alert refers to, in slot order |
| data 9 | `message` | `varStringEncoding` | Human-readable message |

Groups and text must be written and read in this order, after the fixed fields. The deployment revision traces every output back to the exact code and parameters that produced it.

**`Heartbeat`** (16 bytes)

| Offset | Field | Type | Meaning |
| --: | :--- | :--- | :--- |
| 0 | `sequence` | `Sequence` | Last sequence published on the stream; 0 if nothing was published yet |
| 8 | `timestamp` | `EpochNanos` | Sender clock |

The timestamps measure latency along the path: `exchangeTimestamp → receiveTimestamp` is the network hop, `receiveTimestamp → emitTimestamp` is the time spent inside the pipeline.

Every field is mandatory in version 0: no field uses its null value.

### Versioning

SBE decoders read old and new messages as long as the change is additive. The rules:

1. **Add a field:** append it at the end of the message with `sinceVersion="N"` and bump the schema `version` to `N`. Older decoders skip it thanks to `blockLength`; newer decoders see the null value when reading an older message.
2. **Add a message:** give it a new `templateId`. Consumers ignore template ids they do not know.
3. **Retire a message:** delete it and record its `templateId` as retired in the schema; it is never reused. The other messages are not affected.
4. **Breaking change** (removing, renaming the type of, or reordering a field, or changing a `Decimal64` exponent): create a new schema `id` and run both schemas side by side during the migration.

[`SchemaTest`](src/test/java/com/argus/contracts/sbe/SchemaTest.java) pins the schema id, version, template ids, block lengths and the alignment of 64-bit fields. If it fails, the wire contract changed: check these rules before updating the expected values.

### Usage

The codecs depend on Agrona. On Java 25, any JVM that loads them must start with:

```
--add-exports java.base/jdk.internal.misc=ALL-UNNAMED
```

The parent POM passes it to Surefire and Failsafe through the `agrona.jvm.args` property.

## Durable path (Protobuf)

The schemas live in [`src/main/protobuf`](src/main/protobuf). The [`protobuf-maven-plugin`](https://ascopes.github.io/protobuf-maven-plugin/) downloads `protoc` from Maven Central (same version as `protobuf-java`) and generates the Java classes during `generate-sources` into `target/generated-sources/protobuf`. Generated sources are never committed, and no local `protoc` install is needed.

Each topic is meant to be registered in Apicurio as its own artifact once the registry is running ([#6](https://github.com/MeSeeFoo/argus-quant-engine/issues/6)).

### Conventions

- **One message per topic, one file per message.** The Protobuf package *is* the topic name, `v1` included: topic `argus.market.trades.v1` ↔ package `argus.market.trades.v1` ↔ file `argus/market/trades/v1/trade.proto`. File names are the message name in `lower_snake_case` (`best_bid_ask.proto`).
- **Self-contained files.** No file imports another, so each topic registers in Apicurio without schema references. An enum lives next to the only message that uses it.
- **Java package:** `com.argus.contracts.proto.` + the Protobuf package (`com.argus.contracts.proto.market.trades.v1`), with `java_multiple_files`: every message and enum is a top-level class.
- **Names:** `lower_snake_case` fields, `PascalCase` messages and enums, enum values prefixed with the enum name (`SIDE_BUY`), and value `0` reserved for `*_UNSPECIFIED`.
- **Kafka key:** `VENUE:SYMBOL` (`BINANCE:BTCUSDT`) for market data, so all events of an instrument land on the same partition and stay in order; the deployment id in decimal (`42`) for model outputs, so the outputs of a deployment stay in order.
- **Nested messages** are allowed for the entries of a repeated field (`IndicatorValue.Instrument`); each file still has one top-level message.

### Same criteria as the fast path

A Kafka message carries the same values as its SBE counterpart: the publisher copies fields, it never converts them.

| Concept | SBE | Protobuf |
| :--- | :--- | :--- |
| Decimal | `Decimal64`: `int64` mantissa, constant exponent `-8` | `int64` mantissa, scale `10⁻⁸` (`value = mantissa × 10⁻⁸`) |
| Timestamp | `EpochNanos` (`int64`) | `int64`, nanoseconds since the Unix epoch, UTC |
| Sequence | `Sequence` (`uint64`) | `uint64` |
| Symbol | `char[24]`, US-ASCII | `string`, same content, at most 24 characters |
| Venue | `char[12]`, US-ASCII | `string`, same content, at most 12 characters |

Protobuf does not enforce the length limits: the strings come from SBE messages, where they are enforced. `google.protobuf.Timestamp` and decimal wrapper messages are avoided on purpose: they would need imports, cost bytes, and break the 1:1 mapping with SBE.

Every message mirrors its SBE counterpart field by field, and **its field numbers are the SBE field, group and data ids** (repeated fields map to SBE groups). Enum values match SBE too. `double` is `double` in both.

### Messages

| Topic | Message | Meaning | Route |
| :--- | :--- | :--- | :--- |
| `argus.market.trades.v1` | `Trade` | Same as the SBE `Trade`: an execution on the venue, never corrected. | model-engine → data-archiver, bff |
| `argus.market.quotes.v1` | `BestBidAsk` | Same as the SBE `BestBidAsk`: the top of book, intent rather than execution; each update replaces the previous one. | model-engine → data-archiver, bff |
| `argus.indicators.v1` | `IndicatorValue` | Same as the SBE `IndicatorValue`, **conflated**: the latest value per deployment and instrument, at most once per interval and only if it changed ([ADR 0018](../docs/adr/0018-output-policy-of-the-durable-path.md)). | model-engine → data-archiver, bff |
| `argus.alerts.v1` | `Alert` | Same as the SBE `Alert`: every alert. | model-engine → notification-dispatcher, data-archiver, bff |

`Heartbeat` stays on the fast path: Kafka has its own liveness mechanisms.

The market topics carry **every tick, never aggregated**: there is no candle topic. Candles are computed on request from the stored ticks, at whatever resolution is needed, so the full stream stays available for archiving and for a future export to a data lake. The publisher may drop ticks under backpressure rather than block the fast path; a drop shows up as a gap in `sequence`. See [Market data on Kafka](../README.md#market-data-on-kafka).

**`Trade`**

| # | Field | Type | Meaning |
| --: | :--- | :--- | :--- |
| 1 | `sequence` | `uint64` | Gateway sequence |
| 2 | `trade_id` | `int64` | Trade id on the venue |
| 3 | `price` | decimal | Execution price |
| 4 | `quantity` | decimal | Executed quantity |
| 5 | `exchange_timestamp` | timestamp | Execution time on the venue |
| 6 | `receive_timestamp` | timestamp | Time the gateway received it |
| 7 | `aggressor_side` | `Side` | `SIDE_BUY = 1`, `SIDE_SELL = 2` |
| 8 | `venue` | `string` | |
| 9 | `symbol` | `string` | |

**`BestBidAsk`**

| # | Field | Type | Meaning |
| --: | :--- | :--- | :--- |
| 1 | `sequence` | `uint64` | Gateway sequence, shared with `Trade` |
| 2 | `book_update_id` | `int64` | Book version on the venue; updates not newer than the last one are dropped |
| 3 | `bid_price` | decimal | Best bid |
| 4 | `bid_quantity` | decimal | Quantity at the best bid |
| 5 | `ask_price` | decimal | Best ask |
| 6 | `ask_quantity` | decimal | Quantity at the best ask |
| 7 | `exchange_timestamp` | timestamp | Event time on the venue |
| 8 | `receive_timestamp` | timestamp | Time the gateway received it |
| 9 | `venue` | `string` | |
| 10 | `symbol` | `string` | |

**`IndicatorValue`**

| # | Field | Type | Meaning |
| --: | :--- | :--- | :--- |
| 1 | `sequence` | `uint64` | Monotonic per publisher |
| 2 | `trigger_sequence` | `uint64` | Sequence of the triggering input event |
| 3 | `trigger_exchange_timestamp` | timestamp | Event time of the triggering input |
| 4 | `emit_timestamp` | timestamp | Time the engine published the value |
| 5 | `deployment_id` | `int64` | Deployment that produced the value |
| 6 | `deployment_revision` | `uint32` | Revision of the deployment |
| 7 | `values` | `repeated double` | Outputs in the order declared in `ModelConfiguration`; `NaN` while warming up |
| 8 | `instruments` | `repeated Instrument` | `venue` (1) and `symbol` (2), in slot order |

**`Alert`**

| # | Field | Type | Meaning |
| --: | :--- | :--- | :--- |
| 1 | `sequence` | `uint64` | Monotonic per publisher; the notification dispatcher deduplicates with it |
| 2 | `trigger_sequence` | `uint64` | Sequence of the triggering input event |
| 3 | `trigger_exchange_timestamp` | timestamp | Event time of the triggering input |
| 4 | `emit_timestamp` | timestamp | Time the engine published the alert |
| 5 | `deployment_id` | `int64` | Deployment that raised the alert |
| 6 | `deployment_revision` | `uint32` | Revision of the deployment |
| 7 | `severity` | `Severity` | `SEVERITY_INFO = 1`, `SEVERITY_WARNING = 2`, `SEVERITY_CRITICAL = 3` |
| 8 | `instruments` | `repeated Instrument` | `venue` (1) and `symbol` (2), in slot order |
| 9 | `message` | `string` | Human-readable message |

In proto3 a field equal to its default (`0`, `""`, `*_UNSPECIFIED`) is not written and cannot be told apart from a missing one. Every field is mandatory in `v1`, so a consumer that reads `*_UNSPECIFIED` has received an invalid message.

### Evolving the schemas

Topics live for a long time and old messages are re-read (replays, the archiver catching up), so every change must be **backward compatible**: a new consumer reads old messages, and an old consumer reads new ones. In Apicurio each artifact gets the `BACKWARD_TRANSITIVE` compatibility rule, so the registry rejects a schema that breaks this.

Allowed in `v1`:

1. **Add a field** with a number never used before. Old consumers skip it (and keep it as an unknown field when re-writing the message); new consumers see its default when reading old messages, so the default must mean "not present".
2. **Add an enum value** with a new number. Old consumers read it as `UNRECOGNIZED` and must handle that case (ignore the message or treat it as "other"), never fail.
3. **Remove a field:** delete it and add its number **and** name to `reserved`, so neither is ever reused.

Not allowed in `v1`, because they break consumers:

- Changing a field's number or type, or reusing a number or name, even a removed one.
- Renumbering or removing enum values.
- Changing the decimal scale or the meaning of a field.
- Renaming fields, messages or packages (it breaks JSON and the topic ↔ package mapping).

A breaking change means a **new topic** (`argus.market.trades.v2`) with a new package and file. Producers publish to both topics during the migration, and `v1` is retired once no consumer reads it.

[`SchemaTest`](src/test/java/com/argus/contracts/proto/SchemaTest.java) pins the package of each topic, every field number, name and type, the enum values, and checks every field number against the ids in the SBE schema. It also checks that the current classes read a message written by a newer version. If it fails, the wire contract changed: check these rules before updating the expected values.

On every pull request, [`buf breaking`](https://buf.build/docs/breaking/) compares the schemas with the target branch using the rules in [`buf.yaml`](src/main/protobuf/buf.yaml), which match the list above, and fails the pull request on a breaking change. `buf lint` and `buf format` run in the same check.
