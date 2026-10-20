package com.argus.feedgateway.application;

import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.Instrument;

/**
 * Output port: publishes market data in canonical values, and keeps the stream alive while it is
 * idle. Prices and quantities are {@code Decimal64} mantissas (exponent −8) and timestamps are
 * epoch nanoseconds. Implementations assign the sequence and must neither block nor allocate. One
 * thread calls every method.
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

  /**
   * Called on every duty cycle: publishes a liveness message if nothing was published for a while.
   *
   * @param monotonicNow a monotonic clock in nanoseconds, which schedules the checks
   * @param epochNow sender clock, epoch nanoseconds: liveness only, never event time
   * @return the work done, for the agent's idle strategy
   */
  int heartbeatIfIdle(long monotonicNow, long epochNow);
}
