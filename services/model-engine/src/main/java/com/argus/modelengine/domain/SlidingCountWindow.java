package com.argus.modelengine.domain;

/**
 * The last {@code count} events, or fewer while it fills up.
 *
 * @param <E> the read-only view of the events it holds
 */
final class SlidingCountWindow<E extends MarketEvent> extends EventWindow<E> {

  private final int count;

  /**
   * Creates an empty window.
   *
   * @param store the store it reads
   * @param count how many events, positive
   * @throws IllegalArgumentException if the count is not positive
   */
  SlidingCountWindow(EventStore<E> store, int count) {
    super(store);
    if (count <= 0) {
      throw new IllegalArgumentException("window count must be positive: " + count);
    }
    this.count = count;
  }

  @Override
  void advance(long position, long timestamp) {
    first = Math.max(first, position + 1 - count);
    end = position + 1;
  }

  @Override
  long minimumCapacity() {
    return count;
  }
}
