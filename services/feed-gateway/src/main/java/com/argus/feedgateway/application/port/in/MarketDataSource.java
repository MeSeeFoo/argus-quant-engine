package com.argus.feedgateway.application.port.in;

/**
 * Market data received from a venue (or a recording) and waiting to be decoded. It is an input port
 * because market data enters the gateway through it (ADR 0009), although an inbound adapter
 * implements it and the gateway agent polls it: the source decodes on the agent's thread and hands
 * each event to {@link IngestMarketData}.
 */
public interface MarketDataSource {

  /**
   * Decodes up to {@code limit} pending frames on the calling thread.
   *
   * @return the number of frames decoded, for the agent's idle strategy
   */
  int poll(int limit);
}
