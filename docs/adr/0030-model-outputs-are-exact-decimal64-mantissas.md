# 0030. Model outputs are exact `Decimal64` mantissas

- **Status:** Accepted
- **Date:** 2026-11-12
- **Issues:** #143
- **Amends:** [0004](0004-canonical-message-contracts.md), [0014](0014-one-model-engine-for-alert-rules-and-indicators.md)

## Context

[0014](0014-one-model-engine-for-alert-rules-and-indicators.md) gave `IndicatorValue` its values as `double`, while market data kept `Decimal64` ([0004](0004-canonical-message-contracts.md)), because some computed values, such as a z-score or a correlation, do not fit a fixed scale of 10⁻⁸.

Many outputs are prices or money: a moving average or a VWAP of prices is a price. The quants who write models need them exact, and a `double` is not. Letting each output declare its own type (price or number) would solve both cases, but two ways of doing the same thing confuse model authors and invite using the wrong one.

No service publishes or consumes `IndicatorValue` yet, and until the first version the contracts may change in place.

## Decision

- **Every value of an `IndicatorValue` is a `Decimal64`:** an `int64` mantissa at the scale of 10⁻⁸, as for market data. One representation for prices, quantities and every model output.
- A mantissa of `Long.MIN_VALUE` means the model does not have enough data yet; it replaces `NaN`.
- **In SBE**, the group `values` (id 7, `double`) becomes `valueMantissas` (id 9, `Decimal64`), changed in place; id 7 is retired.
- **In Protobuf** (`argus.indicators.v1`), field 7 `values` is reserved and `repeated int64 value_mantissas = 9` added. The topic stays `v1`, and `buf breaking` still passes. SBE and Protobuf keep the same ids and names.
- **Until the first version is released, the contracts may change in place**, recorded in an ADR when a decision changes. The versioning rules of `contracts/README.md` apply in full from then on.

## Consequences

- Outputs are exact: a price computed by a model can be compared, summed or archived without rounding errors.
- A model's value must fit the scale: anything smaller than 10⁻⁸ is lost, such as the variance of returns or the last digits of a correlation, and anything above about 9.2 × 10¹⁰ cannot be represented. A model whose values do not fit must rescale them (for example, a variance in basis points squared), and say so in the name of the output.
- Models compute in whatever they need internally; only what they publish is a mantissa.
- The archiver and the BFF read one decimal type everywhere.

## Alternatives considered

- **Keep `double`.** Covers any magnitude and `NaN`, but prices published by models would not be exact.
- **A type per output, price (`Decimal64`) or number (`double`).** Exact where it matters and wide where it is needed, but two ways of publishing a value confuse users, who could pick the wrong one.
- **A new schema id and topic (`argus.indicators.v2`).** What the versioning rules require for a breaking change once the system is released; with nothing published yet, it would only add a migration with no one to migrate.
