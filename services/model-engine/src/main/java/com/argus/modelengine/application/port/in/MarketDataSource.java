package com.argus.modelengine.application.port.in;

/**
 * Where market data enters the engine: the reader agent polls it on its own thread (ADR 0009, ADR
 * 0029).
 */
public interface MarketDataSource {

  /**
   * Reads up to {@code limit} messages and hands each one to {@link IngestMarketData}. Never waits:
   * what is not read stays in the source. Reader thread. Allocation-free.
   *
   * @param limit the most messages to read, 0 or more
   * @return the number of messages read
   */
  int poll(int limit);
}
