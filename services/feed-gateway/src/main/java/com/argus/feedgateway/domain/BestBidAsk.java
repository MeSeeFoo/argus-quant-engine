package com.argus.feedgateway.domain;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The top of the book on the venue, in canonical values: prices and quantities as {@code Decimal64}
 * mantissas, timestamps in epoch nanoseconds.
 *
 * <p>Mutable and reused, like {@link Trade}: read it before returning and never keep it.
 */
public final class BestBidAsk {

  private @Nullable Instrument instrument;
  private long bookUpdateId;
  private long bidPrice;
  private long bidQuantity;
  private long askPrice;
  private long askQuantity;
  private long exchangeTimestamp;
  private long receiveTimestamp;

  public Instrument instrument() {
    return Objects.requireNonNull(instrument, "instrument not set");
  }

  public BestBidAsk instrument(Instrument value) {
    instrument = value;
    return this;
  }

  /** Book version on the venue: it only moves forward. */
  public long bookUpdateId() {
    return bookUpdateId;
  }

  public BestBidAsk bookUpdateId(long value) {
    bookUpdateId = value;
    return this;
  }

  public long bidPrice() {
    return bidPrice;
  }

  public long bidQuantity() {
    return bidQuantity;
  }

  public long askPrice() {
    return askPrice;
  }

  public long askQuantity() {
    return askQuantity;
  }

  /** Sets the bid from the venue's decimals: {@code mantissa × 10^exponent}. */
  public BestBidAsk bid(
      long priceMantissa, int priceExponent, long quantityMantissa, int quantityExponent) {
    bidPrice = Decimal64.fromDecimal(priceMantissa, priceExponent);
    bidQuantity = Decimal64.fromDecimal(quantityMantissa, quantityExponent);
    return this;
  }

  /** Sets the ask from the venue's decimals: {@code mantissa × 10^exponent}. */
  public BestBidAsk ask(
      long priceMantissa, int priceExponent, long quantityMantissa, int quantityExponent) {
    askPrice = Decimal64.fromDecimal(priceMantissa, priceExponent);
    askQuantity = Decimal64.fromDecimal(quantityMantissa, quantityExponent);
    return this;
  }

  /** Event time on the venue, epoch nanoseconds. */
  public long exchangeTimestamp() {
    return exchangeTimestamp;
  }

  public BestBidAsk exchangeTimestamp(long value) {
    exchangeTimestamp = value;
    return this;
  }

  /** Time the gateway received it, epoch nanoseconds. */
  public long receiveTimestamp() {
    return receiveTimestamp;
  }

  public BestBidAsk receiveTimestamp(long value) {
    receiveTimestamp = value;
    return this;
  }

  /** Whether every price and quantity fits {@code Decimal64} exactly. */
  public boolean isRepresentable() {
    return bidPrice != Decimal64.NOT_REPRESENTABLE
        && bidQuantity != Decimal64.NOT_REPRESENTABLE
        && askPrice != Decimal64.NOT_REPRESENTABLE
        && askQuantity != Decimal64.NOT_REPRESENTABLE;
  }
}
