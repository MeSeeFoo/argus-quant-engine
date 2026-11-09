package com.argus.feedgateway.adapter.out;

import java.util.concurrent.atomic.AtomicLong;

/**
 * What an output adapter published, counted the same way on every transport. Each adapter has its
 * own instance, and counts its drops in counters of its own: the reasons differ per transport. A
 * message counts once the transport accepts it: for Aeron when the offer succeeds, on the
 * publishing thread; for Kafka when the broker acknowledges it, on Kafka's I/O thread. Read by
 * metrics from any thread.
 */
public class PublicationCounters {

  private final AtomicLong trades = new AtomicLong();
  private final AtomicLong bestBidAsks = new AtomicLong();

  /**
   * {@code Trade} messages the transport accepted.
   *
   * @return the count since the gateway started
   */
  public long trades() {
    return trades.get();
  }

  /**
   * {@code BestBidAsk} messages the transport accepted.
   *
   * @return the count since the gateway started
   */
  public long bestBidAsks() {
    return bestBidAsks.get();
  }

  /** Counts a trade the transport accepted. Allocation-free. */
  public void countTrade() {
    trades.incrementAndGet();
  }

  /** Counts a top-of-book update the transport accepted. Allocation-free. */
  public void countBestBidAsk() {
    bestBidAsks.incrementAndGet();
  }
}
