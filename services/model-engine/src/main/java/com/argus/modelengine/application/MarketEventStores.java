package com.argus.modelengine.application;

import com.argus.modelengine.domain.BestBidAsk;
import com.argus.modelengine.domain.BestBidAskStore;
import com.argus.modelengine.domain.Trade;
import com.argus.modelengine.domain.TradeStore;

/**
 * The engine's stores: one per instrument and kind of event (ADR 0029).
 *
 * <p>Used only on the evaluator thread. Appending allocates nothing once the stores have settled at
 * the size their windows need.
 */
public final class MarketEventStores {

  private final TradeStore[] trades;
  private final BestBidAskStore[] bestBidAsks;

  /**
   * Creates the stores of every instrument and allocates their initial objects.
   *
   * @param instruments the number of instruments, numbered from 0
   * @param tradeLimits the sizes of each trade store
   * @param bestBidAskLimits the sizes of each top-of-book store
   */
  public MarketEventStores(int instruments, Limits tradeLimits, Limits bestBidAskLimits) {
    trades = new TradeStore[instruments];
    bestBidAsks = new BestBidAskStore[instruments];
    for (int i = 0; i < instruments; i++) {
      trades[i] = new TradeStore(tradeLimits.initial(), tradeLimits.maximum());
      bestBidAsks[i] = new BestBidAskStore(bestBidAskLimits.initial(), bestBidAskLimits.maximum());
    }
  }

  /**
   * Copies a trade into the store of its instrument. Evaluator thread.
   *
   * @param trade the trade, valid only during this call
   */
  public void appendTrade(Trade trade) {
    trades[trade.instrumentId()].append(trade);
  }

  /**
   * Copies a top-of-book update into the store of its instrument. Evaluator thread.
   *
   * @param bestBidAsk the update, valid only during this call
   */
  public void appendBestBidAsk(BestBidAsk bestBidAsk) {
    bestBidAsks[bestBidAsk.instrumentId()].append(bestBidAsk);
  }

  /**
   * The trades of an instrument, where windows are created at startup.
   *
   * @param instrumentId the instrument
   * @return its store
   */
  public TradeStore trades(int instrumentId) {
    return trades[instrumentId];
  }

  /**
   * The top-of-book updates of an instrument, where windows are created at startup.
   *
   * @param instrumentId the instrument
   * @return its store
   */
  public BestBidAskStore bestBidAsks(int instrumentId) {
    return bestBidAsks[instrumentId];
  }

  /**
   * How many objects a store may hold.
   *
   * @param initial the number allocated at startup
   * @param maximum the most it may ever hold
   */
  public record Limits(int initial, int maximum) {}
}
