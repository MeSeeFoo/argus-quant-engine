package com.argus.modelengine.application.port.in;

import com.argus.modelengine.domain.marketdata.BestBidAsk;
import com.argus.modelengine.domain.marketdata.Trade;

/**
 * What an inbound adapter hands over for each message it decodes (ADR 0009).
 *
 * <p>Called only on the reader thread, from {@link MarketDataSource#poll}. Allocation-free. The
 * events are views the adapter reuses: each one is valid only during the call that received it.
 */
public interface IngestMarketData {

  /**
   * A trade of an instrument the engine handles.
   *
   * @param session the publisher session it came from
   * @param trade the trade, valid only during this call
   */
  void onTrade(int session, Trade trade);

  /**
   * A top-of-book update of an instrument the engine handles.
   *
   * @param session the publisher session it came from
   * @param bestBidAsk the update, valid only during this call
   */
  void onBestBidAsk(int session, BestBidAsk bestBidAsk);

  /**
   * An event the engine does not use, such as one of an instrument it does not handle. It still
   * took a sequence, so without this call it would look like a lost message.
   *
   * @param session the publisher session it came from
   * @param sequence its sequence
   */
  void onIgnored(int session, long sequence);

  /**
   * A heartbeat of an idle stream.
   *
   * @param session the publisher session it came from
   * @param lastPublished the last sequence published on the stream, 0 before the first message
   */
  void onHeartbeat(int session, long lastPublished);
}
