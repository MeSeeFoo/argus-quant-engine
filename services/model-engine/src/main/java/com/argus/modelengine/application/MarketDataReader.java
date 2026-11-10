package com.argus.modelengine.application;

import com.argus.modelengine.application.port.in.IngestMarketData;
import com.argus.modelengine.domain.feed.SequenceGapCounter;
import com.argus.modelengine.domain.feed.TradeDeduplicator;
import com.argus.modelengine.domain.marketdata.BestBidAsk;
import com.argus.modelengine.domain.marketdata.Trade;
import java.util.function.Consumer;

/**
 * What the reader agent does with each message: counts gaps in {@code sequence}, drops trades that
 * are not newer than the last one of their instrument (ADR 0022), and passes the rest on towards
 * the evaluator (ADR 0029).
 *
 * <p>Called only on the reader thread. Allocation-free. Where events go is given as two consumers,
 * created once at startup, so this class does not depend on how they cross to the evaluator.
 */
public final class MarketDataReader implements IngestMarketData {

  private final TradeDeduplicator deduplicator;
  private final SequenceGapCounter gaps;
  private final Consumer<Trade> trades;
  private final Consumer<BestBidAsk> bestBidAsks;

  /**
   * Creates the reader's use case.
   *
   * @param deduplicator drops duplicate and old trades
   * @param gaps counts lost messages
   * @param trades receives each accepted trade, valid only during the call
   * @param bestBidAsks receives each top-of-book update, valid only during the call
   */
  public MarketDataReader(
      TradeDeduplicator deduplicator,
      SequenceGapCounter gaps,
      Consumer<Trade> trades,
      Consumer<BestBidAsk> bestBidAsks) {
    this.deduplicator = deduplicator;
    this.gaps = gaps;
    this.trades = trades;
    this.bestBidAsks = bestBidAsks;
  }

  @Override
  public void onTrade(int session, Trade trade) {
    gaps.onMessage(session, trade.sequence());
    if (deduplicator.accept(trade.instrumentId(), trade.tradeId())) {
      trades.accept(trade);
    }
  }

  @Override
  public void onBestBidAsk(int session, BestBidAsk bestBidAsk) {
    gaps.onMessage(session, bestBidAsk.sequence());
    bestBidAsks.accept(bestBidAsk);
  }

  @Override
  public void onIgnored(int session, long sequence) {
    gaps.onMessage(session, sequence);
  }

  @Override
  public void onHeartbeat(int session, long lastPublished) {
    gaps.onHeartbeat(session, lastPublished);
  }
}
