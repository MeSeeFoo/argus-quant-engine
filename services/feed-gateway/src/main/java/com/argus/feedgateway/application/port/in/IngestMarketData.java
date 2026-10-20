package com.argus.feedgateway.application.port.in;

import com.argus.feedgateway.domain.BestBidAsk;
import com.argus.feedgateway.domain.Trade;

/**
 * Market data decoded from a venue, handed to the gateway to validate and publish. Called on the
 * gateway agent's thread; the objects passed are reused by the caller, so they are only valid
 * during the call.
 */
public interface IngestMarketData {

  void onTrade(Trade trade);

  void onBestBidAsk(BestBidAsk bestBidAsk);
}
