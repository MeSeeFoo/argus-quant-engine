package com.argus.modelengine.domain;

/**
 * Slices of a fixed number of events. A slice closes with its last event.
 *
 * @param <E> the read-only view of the events it holds
 */
final class TumblingCountWindow<E extends MarketEvent> extends TumblingWindow<E> {

  private final int count;

  /**
   * Creates a window with no slices.
   *
   * @param store the store it reads
   * @param count how many events in a slice, positive
   * @throws IllegalArgumentException if the count is not positive
   */
  TumblingCountWindow(EventStore<E> store, int count) {
    super(store);
    if (count <= 0) {
      throw new IllegalArgumentException("window count must be positive: " + count);
    }
    this.count = count;
  }

  @Override
  void advance(long position, long timestamp) {
    openEnd = position + 1;
    if (openEnd - openFirst == count) {
      closeSlice(openEnd);
    }
  }

  /**
   * The closed slice, and an open one one event short of closing.
   *
   * @return the number of events
   */
  @Override
  long minimumCapacity() {
    return 2L * count - 1;
  }
}
