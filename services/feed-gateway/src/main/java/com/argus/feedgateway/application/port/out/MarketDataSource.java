package com.argus.feedgateway.application.port.out;

/**
 * Market data received from a venue (or a recording) and waiting to be decoded. The gateway agent
 * polls it, so it is an output port even though an inbound adapter implements it (ADR 0009): the
 * source decodes on the agent's thread and hands each event to the {@code IngestMarketData} input
 * port.
 */
public interface MarketDataSource {

  /**
   * Decodes up to {@code limit} pending frames on the calling thread.
   *
   * @return the number of frames decoded, for the agent's idle strategy
   */
  int poll(int limit);
}
