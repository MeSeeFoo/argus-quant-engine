package com.argus.feedgateway.adapter.out;

import java.util.concurrent.atomic.AtomicLong;

/**
 * What an output adapter published, counted the same way on every transport. Each adapter has its
 * own instance, and counts its drops in counters of its own: the reasons differ per transport.
 * Written by the publishing thread and read by metrics from any thread.
 */
public class PublicationCounters {

  private final AtomicLong trades = new AtomicLong();
  private final AtomicLong bestBidAsks = new AtomicLong();

  /**
   * {@code Trade} messages handed to the transport.
   *
   * @return the count since the gateway started
   */
  public long trades() {
    return trades.get();
  }

  /**
   * {@code BestBidAsk} messages handed to the transport.
   *
   * @return the count since the gateway started
   */
  public long bestBidAsks() {
    return bestBidAsks.get();
  }

  /** Counts a trade handed to the transport. Allocation-free. */
  public void countTrade() {
    trades.incrementAndGet();
  }

  /** Counts a top-of-book update handed to the transport. Allocation-free. */
  public void countBestBidAsk() {
    bestBidAsks.incrementAndGet();
  }
}
