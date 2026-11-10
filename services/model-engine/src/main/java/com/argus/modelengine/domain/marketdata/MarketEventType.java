package com.argus.modelengine.domain.marketdata;

/** The kinds of market data a model can take as input, each kept in a store of its own. */
public enum MarketEventType {
  /** Executions: {@link Trade}. */
  TRADE,
  /** Top-of-book updates: {@link BestBidAsk}. */
  BEST_BID_ASK
}
