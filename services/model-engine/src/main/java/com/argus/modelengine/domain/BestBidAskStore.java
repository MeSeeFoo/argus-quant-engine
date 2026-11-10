package com.argus.modelengine.domain;

/**
 * The latest top-of-book updates of one instrument. See {@link EventStore}; only used on the
 * evaluator thread.
 */
public final class BestBidAskStore extends EventStore<BestBidAsk> {

  /**
   * Allocates every object the store will ever use.
   *
   * @param capacity how many updates it keeps, at least 1
   * @throws IllegalArgumentException if the capacity is not positive
   */
  public BestBidAskStore(int capacity) {
    super(capacity, MutableBestBidAsk::new);
  }

  /**
   * Copies an update into the store, so {@code update} may be reused afterwards. Evaluator thread.
   * Allocation-free.
   *
   * @param update the update, valid only during this call
   */
  public void append(BestBidAsk update) {
    // The factory given to the constructor only creates MutableBestBidAsk
    ((MutableBestBidAsk) claim(update.exchangeTimestamp())).copyFrom(update);
  }
}
