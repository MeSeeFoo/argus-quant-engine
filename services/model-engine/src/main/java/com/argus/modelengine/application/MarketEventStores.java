package com.argus.modelengine.application;

import com.argus.modelengine.domain.BestBidAsk;
import com.argus.modelengine.domain.BestBidAskStore;
import com.argus.modelengine.domain.Trade;
import com.argus.modelengine.domain.TradeStore;

/**
 * The engine's stores: one per instrument and kind of event, each sized at startup to what its
 * windows need (ADR 0029). The planner works the sizes out from the deployments.
 *
 * <p>Used only on the evaluator thread. Appending allocates nothing.
 */
public final class MarketEventStores {

  private final TradeStore[] trades;
  private final BestBidAskStore[] bestBidAsks;

  /**
   * Creates the stores of every instrument and allocates all their objects.
   *
   * @param tradeCapacities how many trades to keep, per instrument id; at least 1 each
   * @param bestBidAskCapacities how many top-of-book updates to keep, per instrument id; at least 1
   *     each
   * @throws IllegalArgumentException if the arrays differ in length or a capacity is not positive
   */
  public MarketEventStores(int[] tradeCapacities, int[] bestBidAskCapacities) {
    if (tradeCapacities.length != bestBidAskCapacities.length) {
      throw new IllegalArgumentException(
          "one capacity per instrument for each kind of event: "
              + tradeCapacities.length
              + " and "
              + bestBidAskCapacities.length);
    }
    trades = new TradeStore[tradeCapacities.length];
    bestBidAsks = new BestBidAskStore[bestBidAskCapacities.length];
    for (int i = 0; i < trades.length; i++) {
      trades[i] = new TradeStore(tradeCapacities[i]);
      bestBidAsks[i] = new BestBidAskStore(bestBidAskCapacities[i]);
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
}
