package com.argus.feedgateway.application.port.in;

import com.argus.feedgateway.domain.BestBidAsk;
import com.argus.feedgateway.domain.Trade;

/**
 * Market data decoded from a venue, handed to the gateway. The gateway queues each event, and its
 * agent validates and publishes it later on its own thread.
 *
 * <p>Inbound adapters call it from their own threads, and several may call it at once (one per
 * connection), so implementations are thread-safe. A call never blocks and never allocates: when
 * the queue is full, the event is dropped and counted. The objects passed are reused by the caller
 * and only read during the call.
 */
public interface IngestMarketData {

  /** Queues a trade, or drops and counts it if the queue is full. */
  void onTrade(Trade trade);

  /** Queues a top-of-book update, or drops and counts it if the queue is full. */
  void onBestBidAsk(BestBidAsk bestBidAsk);
}
