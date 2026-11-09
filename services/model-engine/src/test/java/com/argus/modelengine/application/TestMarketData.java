package com.argus.modelengine.application;

import com.argus.modelengine.domain.AggressorSide;
import com.argus.modelengine.domain.BestBidAsk;
import com.argus.modelengine.domain.Trade;

/** Events with the values a test needs. Records, so their getters implement the interfaces. */
final class TestMarketData {

  private TestMarketData() {}

  static SampleTrade trade(int instrumentId, long sequence, long tradeId) {
    return new SampleTrade(instrumentId, sequence, 11, 12, tradeId, 100, 200, AggressorSide.BUY);
  }

  static SampleBestBidAsk bestBidAsk(int instrumentId, long sequence) {
    return new SampleBestBidAsk(instrumentId, sequence, 21, 22, 77, 100, 1, 101, 2);
  }

  record SampleTrade(
      int instrumentId,
      long sequence,
      long exchangeTimestamp,
      long receiveTimestamp,
      long tradeId,
      long price,
      long quantity,
      AggressorSide aggressorSide)
      implements Trade {}

  record SampleBestBidAsk(
      int instrumentId,
      long sequence,
      long exchangeTimestamp,
      long receiveTimestamp,
      long bookUpdateId,
      long bidPrice,
      long bidQuantity,
      long askPrice,
      long askQuantity)
      implements BestBidAsk {}
}
