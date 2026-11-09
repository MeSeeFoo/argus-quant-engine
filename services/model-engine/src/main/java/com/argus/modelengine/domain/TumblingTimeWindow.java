package com.argus.modelengine.domain;

/**
 * Slices of a fixed length of event time, aligned to the epoch: each slice is {@code [start, start
 * + duration)}.
 *
 * <p>A slice closes when the first event of a later slice arrives, as there is no clock to close it
 * sooner: the last slice of a quiet market stays open until the next event. A late event whose
 * timestamp belongs to an older slice stays in the open one, where it arrived.
 *
 * @param <E> the read-only view of the events it holds
 */
public final class TumblingTimeWindow<E extends MarketEvent> extends TumblingWindow<E> {

  private final long durationNanos;
  private long openStart = NO_TIMESTAMP;
  private long closedStart = NO_TIMESTAMP;

  /**
   * Creates a window with no slices.
   *
   * @param store the store it reads
   * @param durationNanos the length of a slice in nanoseconds of event time, positive
   * @throws IllegalArgumentException if the duration is not positive
   */
  TumblingTimeWindow(EventStore<E> store, long durationNanos) {
    super(store);
    if (durationNanos <= 0) {
      throw new IllegalArgumentException("window duration must be positive: " + durationNanos);
    }
    this.durationNanos = durationNanos;
  }

  @Override
  void advance(long position, long timestamp) {
    long start = timestamp - Math.floorMod(timestamp, durationNanos);
    if (openStart == NO_TIMESTAMP) {
      openStart = start;
    } else if (start > openStart) {
      closeSlice(position);
      closedStart = openStart;
      openStart = start;
    }
    openEnd = position + 1;
  }

  /**
   * Where the closed slice starts.
   *
   * @return epoch nanoseconds of event time, or {@link #NO_TIMESTAMP} before the first slice closes
   */
  public long closedStart() {
    return closedStart;
  }
}
