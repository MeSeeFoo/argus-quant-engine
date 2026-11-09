package com.argus.feedgateway.application.port.out;

import com.argus.feedgateway.domain.BestBidAsk;
import com.argus.feedgateway.domain.Trade;

/**
 * Publishes market data in canonical values, and the liveness messages of an idle stream.
 * Implementations must neither block nor allocate. One thread calls every method, and the objects
 * it passes are only valid during the call.
 *
 * <p>The caller assigns the sequence, so every transport numbers a message the same (ADR 0027). A
 * message an implementation drops leaves a gap in its stream.
 */
public interface MarketDataPublisher {

  /**
   * Publishes {@code trade} as message {@code sequence}, one more than the last one.
   *
   * @param trade the trade; only valid during this call
   * @param sequence the message's sequence
   */
  void publishTrade(Trade trade, long sequence);

  /**
   * Publishes {@code bestBidAsk} as message {@code sequence}, one more than the last one.
   *
   * @param bestBidAsk the update; only valid during this call
   * @param sequence the message's sequence
   */
  void publishBestBidAsk(BestBidAsk bestBidAsk, long sequence);

  /**
   * Publishes a liveness message, which takes no sequence of its own.
   *
   * @param epochNow sender clock, epoch nanoseconds: liveness only, never event time
   * @param sequence the last one published, 0 before the first message
   */
  void publishHeartbeat(long epochNow, long sequence);
}
