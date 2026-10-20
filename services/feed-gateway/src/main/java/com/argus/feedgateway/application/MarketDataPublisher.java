package com.argus.feedgateway.application;

import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.Instrument;

/**
 * Output port: publishes market data in canonical values. Prices and quantities are {@code
 * Decimal64} mantissas (exponent −8) and timestamps are epoch nanoseconds. Implementations assign
 * the sequence and must neither block nor allocate.
 */
public interface MarketDataPublisher {

  void publishTrade(
      Instrument instrument,
      long tradeId,
      long price,
      long quantity,
      long exchangeTimestamp,
      long receiveTimestamp,
      AggressorSide aggressorSide);

  void publishBestBidAsk(
      Instrument instrument,
      long bookUpdateId,
      long bidPrice,
      long bidQuantity,
      long askPrice,
      long askQuantity,
      long exchangeTimestamp,
      long receiveTimestamp);
}
