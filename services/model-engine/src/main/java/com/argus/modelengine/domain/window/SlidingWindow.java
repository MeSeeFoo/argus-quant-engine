package com.argus.modelengine.domain.window;

import com.argus.modelengine.domain.marketdata.MarketEvent;

/**
 * The last {@code count} events, or fewer while it fills up. Fires on every event.
 *
 * @param <E> the read-only view of the events it holds
 */
final class SlidingWindow<E extends MarketEvent> extends EventWindow<E> {

  /**
   * Creates an empty window.
   *
   * @param store the store it reads
   * @param count how many events, positive
   */
  SlidingWindow(EventStore<E> store, int count) {
    super(store, count);
  }

  @Override
  boolean advance(long position) {
    first = Math.max(first, position + 1 - count);
    end = position + 1;
    return true;
  }
}
