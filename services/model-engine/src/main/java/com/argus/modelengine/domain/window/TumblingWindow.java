package com.argus.modelengine.domain.window;

import com.argus.modelengine.domain.marketdata.MarketEvent;

/**
 * Slices of {@code count} events that do not overlap. {@link #size} and {@link #get} read the last
 * closed slice, which does not change until the next one closes; the window is empty until the
 * first slice closes. A slice closes, and the window fires, with its last event.
 *
 * @param <E> the read-only view of the events it holds
 */
final class TumblingWindow<E extends MarketEvent> extends EventWindow<E> {

  // First position of the slice still filling up
  private long openFirst;

  /**
   * Creates a window with no slices.
   *
   * @param store the store it reads
   * @param count how many events in a slice, positive
   */
  TumblingWindow(EventStore<E> store, int count) {
    super(store, count);
    this.openFirst = first;
  }

  @Override
  boolean advance(long position) {
    if (position + 1 - openFirst < count) {
      return false;
    }
    first = openFirst;
    end = position + 1;
    openFirst = end;
    return true;
  }
}
