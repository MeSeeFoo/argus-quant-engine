package com.argus.feedgateway.application;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Market data the gateway decoded but did not publish, and why. Written by the threads of the
 * inbound adapters, through the {@link MarketDataQueue}, and by the gateway agent; read by metrics
 * from any thread. What an adapter rejects before decoding is counted by that adapter.
 */
public final class MarketDataCounters {

  private final AtomicLong unrepresentableValues = new AtomicLong();
  private final AtomicLong staleBookUpdates = new AtomicLong();
  private final AtomicLong unknownInstruments = new AtomicLong();
  private final AtomicLong droppedEvents = new AtomicLong();

  /**
   * Trades and book updates rejected because a price or quantity does not fit {@code Decimal64}.
   *
   * @return the count since the gateway started
   */
  public long unrepresentableValues() {
    return unrepresentableValues.get();
  }

  /**
   * Top-of-book updates not newer than the last one of their instrument.
   *
   * @return the count since the gateway started
   */
  public long staleBookUpdates() {
    return staleBookUpdates.get();
  }

  /**
   * Trades and book updates for an instrument the gateway does not relay.
   *
   * @return the count since the gateway started
   */
  public long unknownInstruments() {
    return unknownInstruments.get();
  }

  /**
   * Trades and book updates dropped because the queue was full: the agent had fallen behind.
   *
   * @return the count since the gateway started
   */
  public long droppedEvents() {
    return droppedEvents.get();
  }

  /** Counts an event whose values do not fit {@code Decimal64}. Allocation-free. */
  void unrepresentableValue() {
    unrepresentableValues.incrementAndGet();
  }

  /** Counts a stale top-of-book update. Allocation-free. */
  void staleBookUpdate() {
    staleBookUpdates.incrementAndGet();
  }

  /** Counts an event for an instrument the gateway does not relay. Allocation-free. */
  void unknownInstrument() {
    unknownInstruments.incrementAndGet();
  }

  /** Counts an event dropped with the queue full. Allocation-free. */
  void droppedEvent() {
    droppedEvents.incrementAndGet();
  }
}
