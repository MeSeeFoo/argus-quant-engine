package com.argus.modelengine.domain;

/**
 * The events of the last {@code durationNanos} of event time: {@code (now - duration, now]}.
 *
 * <p>The range is contiguous in arrival order and its start only moves past events that are too
 * old. So a late event that arrives inside the range stays there, even if its own timestamp is
 * older than the window, and leaves when the start reaches it.
 *
 * @param <E> the read-only view of the events it holds
 */
final class SlidingTimeWindow<E extends MarketEvent> extends EventWindow<E> {

  private final long durationNanos;

  /**
   * Creates an empty window.
   *
   * @param store the store it reads
   * @param durationNanos its length in nanoseconds of event time, positive
   * @throws IllegalArgumentException if the duration is not positive
   */
  SlidingTimeWindow(EventStore<E> store, long durationNanos) {
    super(store);
    if (durationNanos <= 0) {
      throw new IllegalArgumentException("window duration must be positive: " + durationNanos);
    }
    this.durationNanos = durationNanos;
  }

  @Override
  void advance(long position, long timestamp) {
    long cutoff = now() - durationNanos;
    while (first < position && store.timestampAt(first) <= cutoff) {
      first++;
    }
    end = position + 1;
  }

  /**
   * Whether any event this window lost would still be inside it.
   *
   * @return {@code true} if every lost event is already older than the window
   */
  @Override
  public boolean isComplete() {
    return lostTimestamp == NO_TIMESTAMP || lostTimestamp <= now() - durationNanos;
  }
}
