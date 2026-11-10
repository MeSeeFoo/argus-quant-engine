package com.argus.modelengine.domain.marketdata;

/**
 * What every market data event has, read-only.
 *
 * <p>The object behind this view is reused by the engine: a reference is valid only during the
 * evaluation that received it, on the evaluator thread. A model that needs a value later copies the
 * value (ADR 0029).
 */
public interface MarketEvent {

  /**
   * The instrument of this event, as numbered by {@link
   * com.argus.modelengine.domain.instrument.Instruments}.
   *
   * @return its index in the engine's instruments
   */
  int instrumentId();

  /**
   * The publisher's sequence of this event.
   *
   * @return the sequence on the fast path, shared by trades and top-of-book updates
   */
  long sequence();

  /**
   * When the event happened on the venue: its event time, and the only time windows use (ADR 0017).
   *
   * @return epoch nanoseconds
   */
  long exchangeTimestamp();

  /**
   * When the gateway received the event, on its wall clock. Only for measuring latency.
   *
   * @return epoch nanoseconds
   */
  long receiveTimestamp();
}
