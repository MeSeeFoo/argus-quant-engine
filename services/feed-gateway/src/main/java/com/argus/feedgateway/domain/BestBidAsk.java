package com.argus.feedgateway.domain;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The top of the book on the venue, in canonical values: prices and quantities as {@code Decimal64}
 * mantissas, timestamps in epoch nanoseconds.
 *
 * <p>Mutable and reused, like {@link Trade}: read it before returning and never keep it. To keep
 * its values, {@linkplain #copyFrom copy them} into a {@code BestBidAsk} of your own.
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

  /**
   * Overwrites this one with every value of {@code other}, without allocating. Both then share the
   * same {@link Instrument}, which is immutable; changing either one afterwards does not affect the
   * other.
   *
   * @param other the values to copy
   * @return this instance
   */
  public BestBidAsk copyFrom(BestBidAsk other) {
    instrument = other.instrument;
    bookUpdateId = other.bookUpdateId;
    bidPrice = other.bidPrice;
    bidQuantity = other.bidQuantity;
    askPrice = other.askPrice;
    askQuantity = other.askQuantity;
    exchangeTimestamp = other.exchangeTimestamp;
    receiveTimestamp = other.receiveTimestamp;
    return this;
  }

  /**
   * The quoted instrument.
   *
   * @return the instrument, never {@code null}
   * @throws NullPointerException if none has been set yet
   */
  public Instrument instrument() {
    return Objects.requireNonNull(instrument, "instrument not set");
  }

  public BestBidAsk instrument(Instrument value) {
    instrument = value;
    return this;
  }

  /**
   * Book version on the venue: it only moves forward.
   *
   * @return the venue's update id
   */
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

  /**
   * Sets the bid from the venue's decimals: {@code mantissa × 10^exponent}.
   *
   * @param priceMantissa the venue's price mantissa
   * @param priceExponent the venue's price exponent
   * @param quantityMantissa the venue's quantity mantissa
   * @param quantityExponent the venue's quantity exponent
   * @return this instance
   */
  public BestBidAsk bid(
      long priceMantissa, int priceExponent, long quantityMantissa, int quantityExponent) {
    bidPrice = Decimal64.fromDecimal(priceMantissa, priceExponent);
    bidQuantity = Decimal64.fromDecimal(quantityMantissa, quantityExponent);
    return this;
  }

  /**
   * Sets the ask from the venue's decimals: {@code mantissa × 10^exponent}.
   *
   * @param priceMantissa the venue's price mantissa
   * @param priceExponent the venue's price exponent
   * @param quantityMantissa the venue's quantity mantissa
   * @param quantityExponent the venue's quantity exponent
   * @return this instance
   */
  public BestBidAsk ask(
      long priceMantissa, int priceExponent, long quantityMantissa, int quantityExponent) {
    askPrice = Decimal64.fromDecimal(priceMantissa, priceExponent);
    askQuantity = Decimal64.fromDecimal(quantityMantissa, quantityExponent);
    return this;
  }

  /**
   * Time of the update on the venue: its event time.
   *
   * @return epoch nanoseconds
   */
  public long exchangeTimestamp() {
    return exchangeTimestamp;
  }

  public BestBidAsk exchangeTimestamp(long value) {
    exchangeTimestamp = value;
    return this;
  }

  /**
   * Time the gateway received it, on the wall clock. Only for measuring latency, never for windows.
   *
   * @return epoch nanoseconds
   */
  public long receiveTimestamp() {
    return receiveTimestamp;
  }

  public BestBidAsk receiveTimestamp(long value) {
    receiveTimestamp = value;
    return this;
  }

  /**
   * Whether every price and quantity fits {@code Decimal64} exactly.
   *
   * @return {@code true} if all four are representable
   */
  public boolean isRepresentable() {
    return bidPrice != Decimal64.NOT_REPRESENTABLE
        && bidQuantity != Decimal64.NOT_REPRESENTABLE
        && askPrice != Decimal64.NOT_REPRESENTABLE
        && askQuantity != Decimal64.NOT_REPRESENTABLE;
  }
}
