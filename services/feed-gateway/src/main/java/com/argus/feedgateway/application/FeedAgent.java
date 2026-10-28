package com.argus.feedgateway.application;

import org.agrona.concurrent.Agent;
import org.agrona.concurrent.EpochNanoClock;
import org.agrona.concurrent.NanoClock;

/**
 * The gateway's work on a single thread: drains the {@link MarketDataQueue} into the {@link
 * MarketDataRelay}, then lets the relay keep the stream alive. Plain Java, run by an Agrona {@code
 * AgentRunner} that Spring only starts (ADR 0009, ADR 0010).
 */
public final class FeedAgent implements Agent {

  /** Events drained per duty cycle at most, so heartbeats are never starved. */
  static final int EVENT_LIMIT = 64;

  private final MarketDataQueue queue;
  private final MarketDataRelay relay;
  private final NanoClock monotonicClock;
  private final EpochNanoClock epochClock;

  public FeedAgent(
      MarketDataQueue queue,
      MarketDataRelay relay,
      NanoClock monotonicClock,
      EpochNanoClock epochClock) {
    this.queue = queue;
    this.relay = relay;
    this.monotonicClock = monotonicClock;
    this.epochClock = epochClock;
  }

  @Override
  public int doWork() {
    int work = queue.drain(EVENT_LIMIT);
    return work + relay.heartbeatIfIdle(monotonicClock.nanoTime(), epochClock.nanoTime());
  }

  @Override
  public String roleName() {
    return "feed-gateway";
  }
}
