package com.argus.feedgateway.adapter.out.kafka;

import com.argus.feedgateway.adapter.out.PublicationCounters;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What the Kafka adapter counts: records Kafka acknowledged, events dropped because its ring buffer
 * was full, and records that failed. Written by the gateway agent's thread, the publisher thread
 * and Kafka's I/O thread, and read by metrics from any thread.
 */
public final class KafkaCounters extends PublicationCounters {

  private final AtomicLong droppedRingFull = new AtomicLong();
  private final AtomicLong failedSending = new AtomicLong();

  /** Events dropped because the publisher thread had not drained the ring buffer in time. */
  public long droppedRingFull() {
    return droppedRingFull.get();
  }

  void countDroppedRingFull() {
    droppedRingFull.incrementAndGet();
  }

  /** Records Kafka did not accept, or that could not be handed to it. */
  public long failedSending() {
    return failedSending.get();
  }

  void countFailedSending() {
    failedSending.incrementAndGet();
  }
}
