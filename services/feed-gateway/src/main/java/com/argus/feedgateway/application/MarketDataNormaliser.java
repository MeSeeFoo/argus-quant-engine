package com.argus.feedgateway.application;

import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.BookUpdateFilter;
import com.argus.feedgateway.domain.Decimal64;
import com.argus.feedgateway.domain.Instruments;

/**
 * Turns venue market data into canonical values and hands them to the {@link MarketDataPublisher}.
 * Input adapters decode the venue's format and call it with the venue's decimals (mantissa and
 * exponent) and timestamps already in epoch nanoseconds. A value that does not fit {@code
 * Decimal64} rejects the whole message, never rounds it. Runs on the thread that reads the venue,
 * without allocating.
 */
public final class MarketDataNormaliser {

  private final Instruments instruments;
  private final MarketDataPublisher publisher;
  private final MarketDataCounters counters;
  private final BookUpdateFilter bookUpdates;

  public MarketDataNormaliser(
      Instruments instruments, MarketDataPublisher publisher, MarketDataCounters counters) {
    this.instruments = instruments;
    this.publisher = publisher;
    this.counters = counters;
    this.bookUpdates = new BookUpdateFilter(instruments.size());
  }

  /**
   * A trade.
   *
   * @param instrument index in {@link Instruments}
   * @param exchangeTimestamp execution time on the venue, epoch nanoseconds
   * @param receiveTimestamp time the gateway received it, epoch nanoseconds
   */
  public void onTrade(
      int instrument,
      long tradeId,
      long priceMantissa,
      int priceExponent,
      long quantityMantissa,
      int quantityExponent,
      AggressorSide aggressorSide,
      long exchangeTimestamp,
      long receiveTimestamp) {
    long price = Decimal64.fromDecimal(priceMantissa, priceExponent);
    long quantity = Decimal64.fromDecimal(quantityMantissa, quantityExponent);
    if (price == Decimal64.NOT_REPRESENTABLE || quantity == Decimal64.NOT_REPRESENTABLE) {
      counters.unrepresentableValue();
      return;
    }
    publisher.publishTrade(
        instruments.get(instrument),
        tradeId,
        price,
        quantity,
        exchangeTimestamp,
        receiveTimestamp,
        aggressorSide);
  }

  /**
   * A top-of-book update. Prices share one exponent and quantities another, as on Binance.
   *
   * @param instrument index in {@link Instruments}
   * @param exchangeTimestamp event time on the venue, epoch nanoseconds
   * @param receiveTimestamp time the gateway received it, epoch nanoseconds
   */
  public void onBestBidAsk(
      int instrument,
      long bookUpdateId,
      long bidPriceMantissa,
      long bidQuantityMantissa,
      long askPriceMantissa,
      long askQuantityMantissa,
      int priceExponent,
      int quantityExponent,
      long exchangeTimestamp,
      long receiveTimestamp) {
    if (!bookUpdates.accept(instrument, bookUpdateId)) {
      counters.staleBookUpdate();
      return;
    }
    long bidPrice = Decimal64.fromDecimal(bidPriceMantissa, priceExponent);
    long bidQuantity = Decimal64.fromDecimal(bidQuantityMantissa, quantityExponent);
    long askPrice = Decimal64.fromDecimal(askPriceMantissa, priceExponent);
    long askQuantity = Decimal64.fromDecimal(askQuantityMantissa, quantityExponent);
    if (bidPrice == Decimal64.NOT_REPRESENTABLE
        || bidQuantity == Decimal64.NOT_REPRESENTABLE
        || askPrice == Decimal64.NOT_REPRESENTABLE
        || askQuantity == Decimal64.NOT_REPRESENTABLE) {
      counters.unrepresentableValue();
      return;
    }
    publisher.publishBestBidAsk(
        instruments.get(instrument),
        bookUpdateId,
        bidPrice,
        bidQuantity,
        askPrice,
        askQuantity,
        exchangeTimestamp,
        receiveTimestamp);
  }
}
