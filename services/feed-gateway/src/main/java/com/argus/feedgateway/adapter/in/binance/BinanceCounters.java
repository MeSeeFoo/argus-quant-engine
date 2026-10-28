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

  /** Frames that are not a supported message of a supported schema, or are malformed. */
  public long unsupportedMessages() {
    return unsupportedMessages.get();
  }

  /** Messages for a symbol that is not in the shard. */
  public long unknownSymbols() {
    return unknownSymbols.get();
  }

  /** Frames dropped on arrival because they were larger than the adapter accepts. */
  public long droppedFrames() {
    return droppedFrames.get();
  }

  void unsupportedMessage() {
    unsupportedMessages.incrementAndGet();
  }

  void unknownSymbol() {
    unknownSymbols.incrementAndGet();
  }

  void droppedFrame() {
    droppedFrames.incrementAndGet();
  }
}
