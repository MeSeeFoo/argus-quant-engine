package com.argus.feedgateway.application;

import org.agrona.concurrent.Agent;
import org.agrona.concurrent.EpochNanoClock;
import org.agrona.concurrent.NanoClock;

/**
 * The gateway's hot path, on a single thread: drains the source (decode, normalise, publish) and
 * keeps the stream alive with heartbeats when the venue is quiet. Plain Java, run by an Agrona
 * {@code AgentRunner} (ADR 0010).
 */
public final class FeedAgent implements Agent {

  /** Frames decoded per duty cycle at most, so heartbeats are never starved. */
  static final int FRAME_LIMIT = 64;

  private final MarketDataSource source;
  private final MarketDataPublisher publisher;
  private final NanoClock monotonicClock;
  private final EpochNanoClock epochClock;

  public FeedAgent(
      MarketDataSource source,
      MarketDataPublisher publisher,
      NanoClock monotonicClock,
      EpochNanoClock epochClock) {
    this.source = source;
    this.publisher = publisher;
    this.monotonicClock = monotonicClock;
    this.epochClock = epochClock;
  }

  @Override
  public int doWork() {
    int work = source.poll(FRAME_LIMIT);
    return work + publisher.heartbeatIfIdle(monotonicClock.nanoTime(), epochClock.nanoTime());
  }

  @Override
  public String roleName() {
    return "feed-gateway";
  }
}
