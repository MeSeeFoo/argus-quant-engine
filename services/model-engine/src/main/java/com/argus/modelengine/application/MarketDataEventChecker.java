package com.argus.modelengine.application;

import com.argus.log.RateLimitedLog;
import com.argus.modelengine.domain.feed.Deduplicator;
import com.argus.modelengine.domain.feed.SequenceGapDetector;
import com.argus.modelengine.domain.marketdata.BestBidAsk;
import com.argus.modelengine.domain.marketdata.Trade;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The reader's checks on each message: finds gaps in {@code sequence}, and tells whether a trade or
 * a top-of-book update is new, dropping those that are not newer than the last one of their
 * instrument (ADR 0022). What the checks find is counted in {@link MarketDataCounters}; what to do
 * with an accepted event is the caller's decision.
 *
 * <p>A gap is data lost before it reached the engine, so it is also logged, at most once per
 * interval. Duplicates are only counted: they are expected while a passive gateway takes over.
 *
 * <p>Called only on the reader thread. Allocation-free while nothing is logged.
 */
public final class MarketDataEventChecker {

  private static final Logger LOG = LoggerFactory.getLogger(MarketDataEventChecker.class);
  // A lossy feed may show a gap on every message: the counter counts, the log samples
  private static final Duration GAP_LOG_INTERVAL = Duration.ofSeconds(10);

  private final Deduplicator tradesDeduplicator;
  private final Deduplicator bestBidAsksDeduplicator;
  private final SequenceGapDetector gapDetector;
  private final MarketDataCounters counters;
  private final RateLimitedLog gapLog = new RateLimitedLog(LOG, GAP_LOG_INTERVAL);

  /**
   * Creates the reader's checks.
   *
   * @param tradesDeduplicator drops duplicate and old trades, by {@code tradeId}
   * @param bestBidAsksDeduplicator drops duplicate and old top-of-book updates, by {@code
   *     bookUpdateId}
   * @param gapDetector finds lost messages
   * @param counters where sessions, lost messages and duplicates are counted
   */
  public MarketDataEventChecker(
      Deduplicator tradesDeduplicator,
      Deduplicator bestBidAsksDeduplicator,
      SequenceGapDetector gapDetector,
      MarketDataCounters counters) {
    this.tradesDeduplicator = tradesDeduplicator;
    this.bestBidAsksDeduplicator = bestBidAsksDeduplicator;
    this.gapDetector = gapDetector;
    this.counters = counters;
  }

  /**
   * Checks a trade. Its sequence is checked even when the trade is dropped.
   *
   * @param session the publisher session it came from
   * @param trade the trade, valid only during this call
   * @return {@code true} if it is newer than the last trade of its instrument
   */
  boolean onTrade(int session, Trade trade) {
    onSequenceChecked(gapDetector.onMessage(session, trade.sequence()));
    boolean isNew = tradesDeduplicator.accept(trade.instrumentId(), trade.tradeId());
    if (!isNew) {
      counters.countDuplicateTrade();
    }
    return isNew;
  }

  /**
   * Checks a top-of-book update. Its sequence is checked even when the update is dropped.
   *
   * @param session the publisher session it came from
   * @param bestBidAsk the update, valid only during this call
   * @return {@code true} if it is newer than the last update of its instrument
   */
  boolean onBestBidAsk(int session, BestBidAsk bestBidAsk) {
    onSequenceChecked(gapDetector.onMessage(session, bestBidAsk.sequence()));
    boolean isNew =
        bestBidAsksDeduplicator.accept(bestBidAsk.instrumentId(), bestBidAsk.bookUpdateId());
    if (!isNew) {
      counters.countDuplicateBestBidAsk();
    }
    return isNew;
  }

  /**
   * Checks the sequence of an event the engine does not use, so it is not taken for a gap.
   *
   * @param session the publisher session it came from
   * @param sequence its sequence
   */
  void onIgnored(int session, long sequence) {
    onSequenceChecked(gapDetector.onMessage(session, sequence));
  }

  /**
   * Checks what a heartbeat reveals as lost.
   *
   * @param session the publisher session it came from
   * @param lastPublished the last sequence published on the stream, 0 before the first message
   */
  void onHeartbeat(int session, long lastPublished) {
    onSequenceChecked(gapDetector.onHeartbeat(session, lastPublished));
  }

  /**
   * Counts what the gap detector found: a new publisher session, or messages lost, which are also
   * logged at most once per interval. Allocation-free while nothing is logged.
   *
   * @param found what the last message revealed: lost messages, 0, or {@link
   *     SequenceGapDetector#NEW_SESSION}
   */
  private void onSequenceChecked(long found) {
    if (found == SequenceGapDetector.NEW_SESSION) {
      counters.countPublisherSession();
    } else if (found > 0) {
      gapLog.warn(
          "Messages from the gateway were lost (a jump in sequence)",
          counters.countMissedMessages(found));
    }
  }
}
