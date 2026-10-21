package com.argus.feedgateway.application;

import com.argus.feedgateway.application.port.in.IngestMarketData;
import com.argus.feedgateway.application.port.out.MarketDataPublisher;
import com.argus.feedgateway.domain.BestBidAsk;
import com.argus.feedgateway.domain.BookUpdateFilter;
import com.argus.feedgateway.domain.Instruments;
import com.argus.feedgateway.domain.Trade;

/**
 * The gateway's use case: relays the venue's market data to the fast path, and keeps the stream
 * alive while the venue is quiet.
 *
 * <ul>
 *   <li>A top-of-book update for an instrument the gateway does not relay is dropped: the filter of
 *       stale updates keeps state only for the gateway's instruments.
 *   <li>A trade or top-of-book update whose prices or quantities do not fit {@code Decimal64} is
 *       rejected as a whole, never rounded.
 *   <li>A top-of-book update not newer than the last one of its instrument is dropped.
 *   <li>{@link #heartbeatIfIdle} asks for a heartbeat when nothing was published during a whole
 *       interval, so an idle stream carries one every one to two intervals.
 * </ul>
 *
 * <p>Runs on the gateway agent's thread, without allocating.
 */
public final class MarketDataRelay implements IngestMarketData {

  private final Instruments instruments;
  private final MarketDataPublisher publisher;
  private final MarketDataCounters counters;
  private final BookUpdateFilter bookUpdates;
  private final long heartbeatIntervalNanos;

  private long nextHeartbeatCheck;
  // False until the first check, which asks for a heartbeat straight away
  private boolean heartbeatScheduled;
  private boolean publishedSinceLastCheck;

  public MarketDataRelay(
      Instruments instruments,
      MarketDataPublisher publisher,
      MarketDataCounters counters,
      long heartbeatIntervalNanos) {
    if (heartbeatIntervalNanos <= 0) {
      throw new IllegalArgumentException("heartbeat interval must be positive");
    }
    this.instruments = instruments;
    this.publisher = publisher;
    this.counters = counters;
    this.bookUpdates = new BookUpdateFilter(instruments.size());
    this.heartbeatIntervalNanos = heartbeatIntervalNanos;
  }

  @Override
  public void onTrade(Trade trade) {
    if (!trade.isRepresentable()) {
      counters.unrepresentableValue();
      return;
    }
    publisher.publishTrade(trade);
    publishedSinceLastCheck = true;
  }

  @Override
  public void onBestBidAsk(BestBidAsk bestBidAsk) {
    int instrument = instruments.indexOf(bestBidAsk.instrument());
    if (instrument == Instruments.UNKNOWN) {
      counters.unknownInstrument();
      return;
    }
    if (!bookUpdates.accept(instrument, bestBidAsk.bookUpdateId())) {
      counters.staleBookUpdate();
      return;
    }
    if (!bestBidAsk.isRepresentable()) {
      counters.unrepresentableValue();
      return;
    }
    publisher.publishBestBidAsk(bestBidAsk);
    publishedSinceLastCheck = true;
  }

  /**
   * Called on every duty cycle of the gateway agent.
   *
   * @param monotonicNow a monotonic clock in nanoseconds ({@link System#nanoTime()}), which
   *     schedules the checks: a wall clock stepping backwards must not silence the heartbeat
   * @param epochNow sender clock, epoch nanoseconds, written in the heartbeat: liveness only, never
   *     event time
   * @return 1 if a heartbeat was published, 0 otherwise (the agent's work count)
   */
  public int heartbeatIfIdle(long monotonicNow, long epochNow) {
    // Compared by difference: nanoTime may be negative, and may wrap
    if (heartbeatScheduled && monotonicNow - nextHeartbeatCheck < 0) {
      return 0;
    }
    heartbeatScheduled = true;
    nextHeartbeatCheck = monotonicNow + heartbeatIntervalNanos;
    if (publishedSinceLastCheck) {
      publishedSinceLastCheck = false;
      return 0;
    }
    publisher.publishHeartbeat(epochNow);
    return 1;
  }
}
