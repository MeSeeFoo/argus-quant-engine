package com.argus.feedgateway.application;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Market data the gateway decoded but did not publish, and why. Written by the gateway agent and
 * read by metrics from any thread. What an adapter rejects before decoding is counted by that
 * adapter.
 */
public final class MarketDataCounters {

  private final AtomicLong unrepresentableValues = new AtomicLong();
  private final AtomicLong staleBookUpdates = new AtomicLong();

  /**
   * Trades and book updates rejected because a price or quantity does not fit {@code Decimal64}.
   */
  public long unrepresentableValues() {
    return unrepresentableValues.get();
  }

  /** Top-of-book updates not newer than the last one of their instrument. */
  public long staleBookUpdates() {
    return staleBookUpdates.get();
  }

  void unrepresentableValue() {
    unrepresentableValues.incrementAndGet();
  }

  void staleBookUpdate() {
    staleBookUpdates.incrementAndGet();
  }
}
