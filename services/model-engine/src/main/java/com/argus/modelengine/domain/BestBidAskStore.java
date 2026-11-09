package com.argus.modelengine.domain;

/**
 * Every top-of-book update of one instrument. See {@link EventStore}; only used on the evaluator
 * thread.
 */
public final class BestBidAskStore extends EventStore<BestBidAsk> {

  /**
   * Allocates the initial objects.
   *
   * @param initial the number of objects allocated at startup, at least 1
   * @param maximum the most objects it may ever hold, at least {@code initial}
   * @throws IllegalArgumentException if the sizes are not valid
   */
  public BestBidAskStore(int initial, int maximum) {
    super(initial, maximum, MutableBestBidAsk::new);
  }

  /**
   * Copies an update into the store, so {@code update} may be reused afterwards. Evaluator thread.
   * Allocation-free unless the store grows.
   *
   * @param update the update, valid only during this call
   */
  public void append(BestBidAsk update) {
    // The factory given to the constructor only creates MutableBestBidAsk
    ((MutableBestBidAsk) claim(update.exchangeTimestamp())).copyFrom(update);
  }
}
