package com.argus.feedgateway.adapter.out.aeron;

import com.argus.feedgateway.adapter.out.PublicationCounters;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What only the Aeron adapter counts: heartbeats, and the reasons Aeron refuses a message. Written
 * by the publishing thread and read by metrics from any thread.
 */
public final class AeronCounters extends PublicationCounters {

  private final AtomicLong heartbeats = new AtomicLong();
  private final AtomicLong droppedNotConnected = new AtomicLong();
  private final AtomicLong droppedBackPressured = new AtomicLong();
  private final AtomicLong droppedClosed = new AtomicLong();
  private final AtomicLong droppedOther = new AtomicLong();

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
