package com.argus.feedgateway.adapter.out.aeron;

import java.util.concurrent.atomic.AtomicLong;

/**
 * What the fast-path publisher sent and dropped. Written by the publishing thread and read by
 * metrics from any thread.
 */
public final class PublicationCounters {

  private final AtomicLong trades = new AtomicLong();
  private final AtomicLong bestBidAsks = new AtomicLong();
  private final AtomicLong heartbeats = new AtomicLong();
  private final AtomicLong droppedNotConnected = new AtomicLong();
  private final AtomicLong droppedBackPressured = new AtomicLong();
  private final AtomicLong droppedClosed = new AtomicLong();
  private final AtomicLong droppedOther = new AtomicLong();

  /** {@code Trade} messages handed to Aeron. */
  public long trades() {
    return trades.get();
  }

  /** {@code BestBidAsk} messages handed to Aeron. */
  public long bestBidAsks() {
    return bestBidAsks.get();
  }

  /** {@code Heartbeat} messages handed to Aeron. */
  public long heartbeats() {
    return heartbeats.get();
  }

  /** Messages dropped because no subscriber was connected. */
  public long droppedNotConnected() {
    return droppedNotConnected.get();
  }

  /** Messages dropped because subscribers were not keeping up. */
  public long droppedBackPressured() {
    return droppedBackPressured.get();
  }

  /** Messages dropped because the publication was closed or reached its maximum position. */
  public long droppedClosed() {
    return droppedClosed.get();
  }

  /**
   * Messages dropped for any other reason, such as an admin action that persisted after a retry.
   */
  public long droppedOther() {
    return droppedOther.get();
  }

  void countTrade() {
    trades.incrementAndGet();
  }

  void countBestBidAsk() {
    bestBidAsks.incrementAndGet();
  }

  void countHeartbeat() {
    heartbeats.incrementAndGet();
  }

  void countDroppedNotConnected() {
    droppedNotConnected.incrementAndGet();
  }

  void countDroppedBackPressured() {
    droppedBackPressured.incrementAndGet();
  }

  void countDroppedClosed() {
    droppedClosed.incrementAndGet();
  }

  void countDroppedOther() {
    droppedOther.incrementAndGet();
  }
}
