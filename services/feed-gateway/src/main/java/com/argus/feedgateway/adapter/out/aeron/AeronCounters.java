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

  /**
   * {@code Heartbeat} messages handed to Aeron.
   *
   * @return the count since the gateway started
   */
  public long heartbeats() {
    return heartbeats.get();
  }

  /**
   * Messages dropped because no subscriber was connected.
   *
   * @return the count since the gateway started
   */
  public long droppedNotConnected() {
    return droppedNotConnected.get();
  }

  /**
   * Messages dropped because subscribers were not keeping up.
   *
   * @return the count since the gateway started
   */
  public long droppedBackPressured() {
    return droppedBackPressured.get();
  }

  /**
   * Messages dropped because the publication was closed or reached its maximum position.
   *
   * @return the count since the gateway started
   */
  public long droppedClosed() {
    return droppedClosed.get();
  }

  /**
   * Messages dropped for any other reason, such as an admin action that persisted after a retry.
   *
   * @return the count since the gateway started
   */
  public long droppedOther() {
    return droppedOther.get();
  }

  /** Counts a heartbeat handed to Aeron. Allocation-free. */
  void countHeartbeat() {
    heartbeats.incrementAndGet();
  }

  /** Counts a message dropped with no subscriber connected. Allocation-free. */
  void countDroppedNotConnected() {
    droppedNotConnected.incrementAndGet();
  }

  /** Counts a message dropped by back-pressure. Allocation-free. */
  void countDroppedBackPressured() {
    droppedBackPressured.incrementAndGet();
  }

  /** Counts a message dropped on a closed publication. Allocation-free. */
  void countDroppedClosed() {
    droppedClosed.incrementAndGet();
  }

  /** Counts a message dropped for any other reason. Allocation-free. */
  void countDroppedOther() {
    droppedOther.incrementAndGet();
  }
}
