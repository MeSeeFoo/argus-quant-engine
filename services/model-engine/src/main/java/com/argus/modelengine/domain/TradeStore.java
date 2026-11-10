package com.argus.modelengine.domain;

/**
 * The latest trades of one instrument. See {@link EventStore}; only used on the evaluator thread.
 */
public final class TradeStore extends EventStore<Trade> {

  /**
   * Allocates every object the store will ever use.
   *
   * @param capacity how many trades it keeps, at least 1
   * @throws IllegalArgumentException if the capacity is not positive
   */
  public TradeStore(int capacity) {
    super(capacity, MutableTrade::new);
  }

  /**
   * Copies a trade into the store, so {@code trade} may be reused afterwards. Evaluator thread.
   * Allocation-free.
   *
   * @param trade the trade, valid only during this call
   */
  public void append(Trade trade) {
    // The factory given to the constructor only creates MutableTrade
    ((MutableTrade) claim(trade.exchangeTimestamp())).copyFrom(trade);
  }
}
