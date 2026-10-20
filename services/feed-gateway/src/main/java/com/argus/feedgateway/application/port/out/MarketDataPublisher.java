package com.argus.feedgateway.application.port.out;

import com.argus.feedgateway.domain.BestBidAsk;
import com.argus.feedgateway.domain.Trade;

/**
 * Publishes market data in canonical values, and the liveness messages of an idle stream.
 * Implementations assign the sequence, and must neither block nor allocate. One thread calls every
 * method, and the objects it passes are only valid during the call.
 */
public interface MarketDataPublisher {

  void publishTrade(Trade trade);

  void publishBestBidAsk(BestBidAsk bestBidAsk);

  /**
   * Publishes a liveness message carrying the last sequence published.
   *
   * @param epochNow sender clock, epoch nanoseconds: liveness only, never event time
   */
  void publishHeartbeat(long epochNow);
}
