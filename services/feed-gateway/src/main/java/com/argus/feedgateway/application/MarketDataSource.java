package com.argus.feedgateway.application;

/**
 * Input port: market data received from a venue (or a recording) and waiting to be decoded. The
 * gateway agent polls it; the source decodes on the agent's thread and calls the {@link
 * MarketDataNormaliser}.
 */
public interface MarketDataSource {

  /**
   * Decodes up to {@code limit} pending frames on the calling thread.
   *
   * @return the number of frames decoded, for the agent's idle strategy
   */
  int poll(int limit);
}
