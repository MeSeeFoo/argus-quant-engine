package com.argus.modelengine.adapter.in.aeron;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Messages the Aeron adapter read but did not hand over as events. The {@code count} methods are
 * called only on the reader thread and allocate nothing; the values may be read from any thread.
 */
public final class AeronInputCounters {

  private final AtomicLong unknownInstruments = new AtomicLong();
  private final AtomicLong malformed = new AtomicLong();
  private final AtomicLong unsupported = new AtomicLong();

  /** Counts an event of an instrument the engine does not handle. */
  void countUnknownInstrument() {
    unknownInstruments.incrementAndGet();
  }

  /** Counts an event whose fields the engine cannot use. */
  void countMalformed() {
    malformed.incrementAndGet();
  }

  /** Counts a message of another schema or of a template the engine does not read. */
  void countUnsupported() {
    unsupported.incrementAndGet();
  }

  /**
   * Events of instruments the engine does not handle.
   *
   * @return the count since the engine started
   */
  public long unknownInstruments() {
    return unknownInstruments.get();
  }

  /**
   * Events whose fields the engine cannot use.
   *
   * @return the count since the engine started
   */
  public long malformed() {
    return malformed.get();
  }

  /**
   * Messages of another schema or of a template the engine does not read.
   *
   * @return the count since the engine started
   */
  public long unsupported() {
    return unsupported.get();
  }
}
