package com.argus.modelengine.domain;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Drops any trade whose {@code tradeId} is not newer than the last one seen for its instrument: the
 * safety net for a failover and for instruments moving between shards (ADR 0022).
 *
 * <p>{@link #accept} is called only on the reader thread and allocates nothing. {@link
 * #droppedTrades} may be read from any thread.
 */
public final class TradeDeduplicator {

  private final long[] lastTradeIds;
  private final AtomicLong droppedTrades = new AtomicLong();

  /**
   * Creates a deduplicator that has seen no trades.
   *
   * @param instruments the number of instruments, whose ids index its state
   */
  public TradeDeduplicator(int instruments) {
    lastTradeIds = new long[instruments];
    Arrays.fill(lastTradeIds, Long.MIN_VALUE);
  }

  /**
   * Decides whether a trade is new, and remembers it if so. Reader thread. Allocation-free.
   *
   * @param instrumentId the trade's instrument
   * @param tradeId the venue's id of the trade
   * @return {@code true} if it is newer than the last one of its instrument
   */
  public boolean accept(int instrumentId, long tradeId) {
    if (tradeId > lastTradeIds[instrumentId]) {
      lastTradeIds[instrumentId] = tradeId;
      return true;
    }
    droppedTrades.incrementAndGet();
    return false;
  }

  /**
   * Duplicate and old trades dropped. Any thread.
   *
   * @return the count since the engine started
   */
  public long droppedTrades() {
    return droppedTrades.get();
  }
}
