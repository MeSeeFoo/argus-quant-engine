package com.argus.modelengine.domain.window;

import com.argus.modelengine.domain.marketdata.AggressorSide;
import com.argus.modelengine.domain.marketdata.BestBidAsk;
import com.argus.modelengine.domain.marketdata.Trade;

/** Reusable sources of events for the tests: each call overwrites the same object. */
final class TestEvents {

  /** A second of event time, in nanoseconds. */
  static final long SECOND = 1_000_000_000L;

  private final SourceTrade trade = new SourceTrade();
  private final SourceBestBidAsk bestBidAsk = new SourceBestBidAsk();

  /** A trade whose id is its price, at {@code timestamp}. */
  Trade trade(long id, long timestamp) {
    trade.tradeId = id;
    trade.exchangeTimestamp = timestamp;
    return trade;
  }

  /** A top-of-book update whose version is its bid, at {@code timestamp}. */
  BestBidAsk bestBidAsk(long id, long timestamp) {
    bestBidAsk.bookUpdateId = id;
    bestBidAsk.exchangeTimestamp = timestamp;
    return bestBidAsk;
  }

  private static final class SourceTrade implements Trade {

    long tradeId;
    long exchangeTimestamp;

    @Override
    public int instrumentId() {
      return 1;
    }

    @Override
    public long sequence() {
      return tradeId + 100;
    }

    @Override
    public long tradeId() {
      return tradeId;
    }

    @Override
    public long price() {
      return tradeId;
    }

    @Override
    public long quantity() {
      return 2 * tradeId;
    }

    @Override
    public long exchangeTimestamp() {
      return exchangeTimestamp;
    }

    @Override
    public long receiveTimestamp() {
      return exchangeTimestamp + 1;
    }

    @Override
    public AggressorSide aggressorSide() {
      return AggressorSide.SELL;
    }
  }

  private static final class SourceBestBidAsk implements BestBidAsk {

    long bookUpdateId;
    long exchangeTimestamp;

    @Override
    public int instrumentId() {
      return 2;
    }

    @Override
    public long sequence() {
      return bookUpdateId + 100;
    }

    @Override
    public long bookUpdateId() {
      return bookUpdateId;
    }

    @Override
    public long bidPrice() {
      return bookUpdateId;
    }

    @Override
    public long bidQuantity() {
      return bookUpdateId + 1;
    }

    @Override
    public long askPrice() {
      return bookUpdateId + 2;
    }

    @Override
    public long askQuantity() {
      return bookUpdateId + 3;
    }

    @Override
    public long exchangeTimestamp() {
      return exchangeTimestamp;
    }

    @Override
    public long receiveTimestamp() {
      return exchangeTimestamp + 1;
    }
  }
}
