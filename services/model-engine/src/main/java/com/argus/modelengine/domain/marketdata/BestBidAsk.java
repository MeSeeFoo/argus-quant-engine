package com.argus.modelengine.domain.marketdata;

/**
 * A top-of-book update, read-only. Prices and quantities are {@code Decimal64} mantissas (scale
 * 10⁻⁸). See {@link MarketEvent} for how long a reference stays valid.
 */
public interface BestBidAsk extends MarketEvent {

  /**
   * The venue's version of the book.
   *
   * @return the book update id
   */
  long bookUpdateId();

  /**
   * The best bid.
   *
   * @return a {@code Decimal64} mantissa (scale 10⁻⁸)
   */
  long bidPrice();

  /**
   * The quantity at the best bid.
   *
   * @return a {@code Decimal64} mantissa (scale 10⁻⁸)
   */
  long bidQuantity();

  /**
   * The best ask.
   *
   * @return a {@code Decimal64} mantissa (scale 10⁻⁸)
   */
  long askPrice();

  /**
   * The quantity at the best ask.
   *
   * @return a {@code Decimal64} mantissa (scale 10⁻⁸)
   */
  long askQuantity();
}
