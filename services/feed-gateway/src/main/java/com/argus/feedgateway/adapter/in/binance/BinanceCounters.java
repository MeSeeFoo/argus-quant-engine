package com.argus.feedgateway.adapter.in.binance;

import java.util.concurrent.atomic.AtomicLong;

/**
 * What the Binance adapter received but did not hand to the gateway, and why. Written by the
 * connections' threads, read by metrics from any thread.
 */
public final class BinanceCounters {

  private final AtomicLong unsupportedMessages = new AtomicLong();
  private final AtomicLong unknownSymbols = new AtomicLong();
  private final AtomicLong droppedFrames = new AtomicLong();

  /**
   * Frames that are not a supported message of a supported schema, or are malformed.
   *
   * @return the count since the gateway started
   */
  public long unsupportedMessages() {
    return unsupportedMessages.get();
  }

  /**
   * Messages for a symbol that is not in the shard.
   *
   * @return the count since the gateway started
   */
  public long unknownSymbols() {
    return unknownSymbols.get();
  }

  /**
   * Frames dropped on arrival because they were larger than the adapter accepts.
   *
   * @return the count since the gateway started
   */
  public long droppedFrames() {
    return droppedFrames.get();
  }

  /** Counts a frame that could not be used. Allocation-free. */
  void unsupportedMessage() {
    unsupportedMessages.incrementAndGet();
  }

  /** Counts a message for a symbol outside the shard. Allocation-free. */
  void unknownSymbol() {
    unknownSymbols.incrementAndGet();
  }

  /** Counts a frame that was too long. Allocation-free. */
  void droppedFrame() {
    droppedFrames.incrementAndGet();
  }
}
