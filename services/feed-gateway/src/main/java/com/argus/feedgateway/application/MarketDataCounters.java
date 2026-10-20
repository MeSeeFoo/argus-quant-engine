package com.argus.feedgateway.application;

import java.util.concurrent.atomic.AtomicLong;

/**
 * What the gateway received but did not publish, and why. Written by the thread that reads the
 * venue and read by metrics from any thread.
 */
public final class MarketDataCounters {

  private final AtomicLong unsupportedMessages = new AtomicLong();
  private final AtomicLong unknownSymbols = new AtomicLong();
  private final AtomicLong unrepresentableValues = new AtomicLong();
  private final AtomicLong staleBookUpdates = new AtomicLong();
  private final AtomicLong droppedFrames = new AtomicLong();

  /** Frames that are not a supported message of a supported schema, or are malformed. */
  public long unsupportedMessages() {
    return unsupportedMessages.get();
  }

  /** Messages for a symbol that is not in the shard. */
  public long unknownSymbols() {
    return unknownSymbols.get();
  }

  /**
   * Trades and book updates rejected because a price or quantity does not fit {@code Decimal64}.
   */
  public long unrepresentableValues() {
    return unrepresentableValues.get();
  }

  /** Top-of-book updates not newer than the last one of their instrument. */
  public long staleBookUpdates() {
    return staleBookUpdates.get();
  }

  /**
   * Frames dropped on arrival because the gateway agent had not drained the previous ones: the
   * connection never waits for the agent.
   */
  public long droppedFrames() {
    return droppedFrames.get();
  }

  public void unsupportedMessage() {
    unsupportedMessages.incrementAndGet();
  }

  public void unknownSymbol() {
    unknownSymbols.incrementAndGet();
  }

  public void droppedFrame() {
    droppedFrames.incrementAndGet();
  }

  void unrepresentableValue() {
    unrepresentableValues.incrementAndGet();
  }

  void staleBookUpdate() {
    staleBookUpdates.incrementAndGet();
  }
}
