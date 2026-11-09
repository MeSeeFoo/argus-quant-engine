package com.argus.modelengine.domain;

/**
 * An execution on the venue, read-only. Prices and quantities are {@code Decimal64} mantissas
 * (scale 10⁻⁸). See {@link MarketEvent} for how long a reference stays valid.
 */
public interface Trade extends MarketEvent {

  /**
   * The venue's id of this trade.
   *
   * @return the trade id
   */
  long tradeId();

  /**
   * The price of the trade.
   *
   * @return a {@code Decimal64} mantissa (scale 10⁻⁸)
   */
  long price();

  /**
   * The traded quantity.
   *
   * @return a {@code Decimal64} mantissa (scale 10⁻⁸)
   */
  long quantity();

  /**
   * The side that took liquidity.
   *
   * @return the aggressor's side
   */
  AggressorSide aggressorSide();
}
