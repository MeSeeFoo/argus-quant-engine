package com.argus.modelengine.domain;

/** Every trade of one instrument. See {@link EventStore}; only used on the evaluator thread. */
public final class TradeStore extends EventStore<Trade> {

  /**
   * Allocates the initial objects.
   *
   * @param initial the number of objects allocated at startup, at least 1
   * @param maximum the most objects it may ever hold, at least {@code initial}
   * @throws IllegalArgumentException if the sizes are not valid
   */
  public TradeStore(int initial, int maximum) {
    super(initial, maximum, MutableTrade::new);
  }

  /**
   * Copies a trade into the store, so {@code trade} may be reused afterwards. Evaluator thread.
   * Allocation-free unless the store grows.
   *
   * @param trade the trade, valid only during this call
   */
  public void append(Trade trade) {
    // The factory given to the constructor only creates MutableTrade
    ((MutableTrade) claim(trade.exchangeTimestamp())).copyFrom(trade);
  }
}
