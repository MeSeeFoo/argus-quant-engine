package com.argus.feedgateway.adapter.out.kafka;

import com.argus.feedgateway.domain.BestBidAsk;
import java.util.stream.Stream;

/**
 * Keeps the latest top of book of each instrument, with the sequence it was published with, until
 * it is flushed. One pre-allocated slot per instrument, indexed by the instrument's id: conflating
 * and flushing allocate nothing. Used by the sender thread only.
 */
final class BestBidAskConflator {

  /** Receives each top of book a flush hands over. */
  @FunctionalInterface
  interface Sink {
    /**
     * Takes a top of book; it is only valid during this call.
     *
     * @param bestBidAsk the latest top of book of its instrument
     * @param sequence the sequence it was published with
     */
    void accept(BestBidAsk bestBidAsk, long sequence);
  }

  private final BestBidAsk[] latest;
  private final long[] sequences;
  private final boolean[] changed;

  /**
   * Creates one slot for each of {@code instruments} instruments.
   *
   * @param instruments how many instruments the shard has
   */
  BestBidAskConflator(int instruments) {
    this.latest = Stream.generate(BestBidAsk::new).limit(instruments).toArray(BestBidAsk[]::new);
    this.sequences = new long[instruments];
    this.changed = new boolean[instruments];
  }

  /**
   * Overwrites the slot of {@code bestBidAsk}'s instrument.
   *
   * @param bestBidAsk the update; copied, so it can be reused afterwards
   * @param sequence the sequence it was published with
   */
  void conflate(BestBidAsk bestBidAsk, long sequence) {
    int instrumentId = bestBidAsk.instrument().id();
    latest[instrumentId].copyFrom(bestBidAsk);
    sequences[instrumentId] = sequence;
    changed[instrumentId] = true;
  }

  /**
   * Hands {@code sink} the slot of every instrument that changed since the last flush, and marks
   * them unchanged.
   *
   * @param sink receives each one; it must not throw
   * @return how many it handed over
   */
  int flush(Sink sink) {
    int flushed = 0;
    for (int instrumentId = 0; instrumentId < changed.length; instrumentId++) {
      if (changed[instrumentId]) {
        changed[instrumentId] = false;
        flushed++;
        sink.accept(latest[instrumentId], sequences[instrumentId]);
      }
    }
    return flushed;
  }
}
