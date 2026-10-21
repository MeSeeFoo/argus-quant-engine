package com.argus.feedgateway.application;

import com.argus.feedgateway.application.port.in.MarketDataSource;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.EpochNanoClock;
import org.agrona.concurrent.NanoClock;

/**
 * The gateway's work on a single thread: drains the source, whose events reach the {@link
 * MarketDataRelay}, then lets the relay keep the stream alive. Plain Java, run by an Agrona {@code
 * AgentRunner} that Spring only starts (ADR 0009, ADR 0010).
 */
public final class FeedAgent implements Agent {

  /** Frames decoded per duty cycle at most, so heartbeats are never starved. */
  static final int FRAME_LIMIT = 64;

  private final MarketDataSource source;
  private final MarketDataRelay relay;
  private final NanoClock monotonicClock;
  private final EpochNanoClock epochClock;

  public FeedAgent(
      MarketDataSource source,
      MarketDataRelay relay,
      NanoClock monotonicClock,
      EpochNanoClock epochClock) {
    this.source = source;
    this.relay = relay;
    this.monotonicClock = monotonicClock;
    this.epochClock = epochClock;
  }

  @Override
  public int doWork() {
    int work = source.poll(FRAME_LIMIT);
    return work + relay.heartbeatIfIdle(monotonicClock.nanoTime(), epochClock.nanoTime());
  }

  @Override
  public String roleName() {
    return "feed-gateway";
  }
}
