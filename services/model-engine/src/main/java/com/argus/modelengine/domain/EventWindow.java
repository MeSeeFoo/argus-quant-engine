package com.argus.modelengine.domain;

import java.util.Objects;

/**
 * A range of positions over an {@link EventStore}, declared by a model by number of events: it
 * copies no events, so any number of windows share the same objects (ADR 0029).
 *
 * <p>Events stay in arrival order and are never reordered, so a replay gives the same result as the
 * original run. The window's "now" is event time, {@code exchangeTimestamp}, never the system
 * clock.
 *
 * <p>Used only on the evaluator thread, and read between appends to its store. Reading allocates
 * nothing. An event returned by {@link #get} is valid only during the current evaluation.
 *
 * @param <E> the read-only view of the events it holds
 */
public abstract sealed class EventWindow<E extends MarketEvent>
    permits SlidingWindow, TumblingWindow {

  /** {@link #now} before the first event: no event has that timestamp. */
  public static final long NO_TIMESTAMP = Long.MIN_VALUE;

  final EventStore<E> store;
  final int count;
  // The range a model reads: positions first (inclusive) to end (exclusive)
  long first;
  long end;

  private long now = NO_TIMESTAMP;
  private boolean fired;

  /**
   * Creates an empty window, starting at the store's next event.
   *
   * @param store the store it reads
   * @param count the window's length, or a slice's, in events
   */
  EventWindow(EventStore<E> store, int count) {
    this.store = store;
    this.count = count;
    this.first = store.nextPosition();
    this.end = first;
  }

  /**
   * Moves the window for an event about to be written at {@code position}. Allocation-free.
   *
   * @param position the new event's position
   * @param timestamp its {@code exchangeTimestamp}, in epoch nanoseconds
   */
  final void onAppend(long position, long timestamp) {
    now = Math.max(now, timestamp);
    fired = advance(position);
  }

  /**
   * Moves this window's range to include the event at {@code position}, which is written right
   * after this call. Allocation-free.
   *
   * @param position the new event's position
   * @return {@code true} if the window fires for this event
   */
  abstract boolean advance(long position);

  /**
   * Whether the latest event of the store made this window fire: a sliding window fires on every
   * event, a tumbling one when a slice closes. The evaluator checks it after each append to run the
   * models of the window.
   *
   * @return {@code true} until the next append to the store
   */
  public final boolean hasFired() {
    return fired;
  }

  /**
   * How many events the window holds: fewer than its count while it fills up.
   *
   * @return the number of events
   */
  public final int size() {
    return (int) (end - first);
  }

  /**
   * An event of the window, oldest first in arrival order. Allocation-free.
   *
   * @param index from 0 to {@link #size} (exclusive)
   * @return the event, valid only during the current evaluation
   * @throws IndexOutOfBoundsException if {@code index} is out of range
   */
  public final E get(int index) {
    return store.at(first + Objects.checkIndex(index, size()));
  }

  /**
   * The window's "now": the latest {@code exchangeTimestamp} of its input. Never goes backwards.
   *
   * @return epoch nanoseconds, or {@link #NO_TIMESTAMP} before the first event
   */
  public final long now() {
    return now;
  }
}
