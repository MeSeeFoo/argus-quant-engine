package com.argus.modelengine.domain.marketdata;

/** The kinds of market data a model can take as input, each with windows of its own. */
public enum MarketEventType {
  /** Executions: {@link Trade}. */
  TRADE(0),
  /** Top-of-book updates: {@link BestBidAsk}. */
  BEST_BID_ASK(1);

  /** How many kinds there are: every {@link #index} is below it. */
  public static final int COUNT = 2;

  private final int index;

  /**
   * Gives a kind its index.
   *
   * @param index from 0 to {@link #COUNT} (exclusive), unique
   */
  MarketEventType(int index) {
    this.index = index;
  }

  /**
   * A position for tables indexed by kind of event, such as the evaluator's. Explicit, rather than
   * the enum's ordinal, so reordering the constants never changes it.
   *
   * @return from 0 to {@link #COUNT} (exclusive)
   */
  public int index() {
    return index;
  }
}
