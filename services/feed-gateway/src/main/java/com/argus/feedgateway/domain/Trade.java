package com.argus.feedgateway.domain;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * An execution on the venue, in canonical values: prices and quantities as {@code Decimal64}
 * mantissas, timestamps in epoch nanoseconds.
 *
 * <p>Mutable and reused: whoever receives trades fills one instance per trade and passes it on, so
 * the hot path allocates nothing. Whoever is passed one must read it before returning and never
 * keep it.
 */
public final class Trade {

  private @Nullable Instrument instrument;
  private long tradeId;
  private long price;
  private long quantity;
  private long exchangeTimestamp;
  private long receiveTimestamp;
  private AggressorSide aggressorSide = AggressorSide.BUY;

  public Instrument instrument() {
    return Objects.requireNonNull(instrument, "instrument not set");
  }

  public Trade instrument(Instrument value) {
    instrument = value;
    return this;
  }

  public long tradeId() {
    return tradeId;
  }

  public Trade tradeId(long value) {
    tradeId = value;
    return this;
  }

  /** The price as a {@code Decimal64} mantissa, or {@link Decimal64#NOT_REPRESENTABLE}. */
  public long price() {
    return price;
  }

  /** Sets the price from the venue's decimal: {@code mantissa × 10^exponent}. */
  public Trade price(long mantissa, int exponent) {
    price = Decimal64.fromDecimal(mantissa, exponent);
    return this;
  }

  /** The quantity as a {@code Decimal64} mantissa, or {@link Decimal64#NOT_REPRESENTABLE}. */
  public long quantity() {
    return quantity;
  }

  /** Sets the quantity from the venue's decimal: {@code mantissa × 10^exponent}. */
  public Trade quantity(long mantissa, int exponent) {
    quantity = Decimal64.fromDecimal(mantissa, exponent);
    return this;
  }

  /** Execution time on the venue, epoch nanoseconds. */
  public long exchangeTimestamp() {
    return exchangeTimestamp;
  }

  public Trade exchangeTimestamp(long value) {
    exchangeTimestamp = value;
    return this;
  }

  /** Time the gateway received it, epoch nanoseconds. */
  public long receiveTimestamp() {
    return receiveTimestamp;
  }

  public Trade receiveTimestamp(long value) {
    receiveTimestamp = value;
    return this;
  }

  public AggressorSide aggressorSide() {
    return aggressorSide;
  }

  public Trade aggressorSide(AggressorSide value) {
    aggressorSide = value;
    return this;
  }

  /**
   * Whether price and quantity fit {@code Decimal64} exactly; a trade that does not is rejected.
   */
  public boolean isRepresentable() {
    return price != Decimal64.NOT_REPRESENTABLE && quantity != Decimal64.NOT_REPRESENTABLE;
  }
}
