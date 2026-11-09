package com.argus.modelengine.domain;

import java.util.Objects;

/**
 * A range of positions over an {@link EventStore}, declared by a model: it copies no events, so any
 * number of windows share the same objects (ADR 0029).
 *
 * <p>Time is always {@code exchangeTimestamp}, never the system clock. Events stay in arrival order
 * and are never reordered: a late event stays where it arrived, so a replay gives the same result
 * as the original run.
 *
 * <p>Used only on the evaluator thread, and read between appends to its store. Reading allocates
 * nothing. An event returned by {@link #get} is valid only during the current evaluation.
 *
 * @param <E> the read-only view of the events it holds
 */
public abstract sealed class EventWindow<E extends MarketEvent>
    permits SlidingTimeWindow, SlidingCountWindow, TumblingWindow {

  /** {@link #now} before the first event: no event has that timestamp. */
  public static final long NO_TIMESTAMP = Long.MIN_VALUE;

  final EventStore<E> store;
  // The range a model reads: positions first (inclusive) to end (exclusive)
  long first;
  long end;
  // The newest event time among the events this window lost
  long lostTimestamp = NO_TIMESTAMP;

  private long now = NO_TIMESTAMP;
  private long lateEvents;

  /**
   * Creates an empty window, starting at the store's next event.
   *
   * @param store the store it reads
   */
  EventWindow(EventStore<E> store) {
    this.store = store;
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
    if (timestamp < now) {
      lateEvents++;
    } else {
      now = timestamp;
    }
    advance(position, timestamp);
  }

  /**
   * Moves this window's range. The event at {@code position} is not written yet, so only earlier
   * positions may be read. Allocation-free.
   *
   * @param position the new event's position
   * @param timestamp its {@code exchangeTimestamp}, in epoch nanoseconds
   */
  abstract void advance(long position, long timestamp);

  /**
   * The oldest position this window still needs from the store. Never goes backwards.
   *
   * @return that position
   */
  long firstNeeded() {
    return first;
  }

  /**
   * Tells this window that the store overwrote an event it needed.
   *
   * @param position the lost event's position, the oldest this window held
   * @param timestamp the lost event's {@code exchangeTimestamp}, in epoch nanoseconds
   */
  void onLost(long position, long timestamp) {
    first = position + 1;
    lostTimestamp = Math.max(lostTimestamp, timestamp);
  }

  /**
   * The size the store must be able to reach for this window to work. Checked at startup.
   *
   * @return the number of events, 1 for a window whose need depends on the event rate
   */
  long minimumCapacity() {
    return 1;
  }

  /**
   * Whether the window holds everything it asked for. A window that lost events to the store's
   * maximum reports it here; one that is still filling up is not incomplete, {@link #size} tells
   * how much it has.
   *
   * @return {@code true} if no event of the current range was lost
   */
  public boolean isComplete() {
    return lostTimestamp == NO_TIMESTAMP;
  }

  /**
   * How many events the window holds.
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

  /**
   * Events that arrived with a timestamp older than {@link #now}.
   *
   * @return the count since the window was created
   */
  public final long lateEvents() {
    return lateEvents;
  }
}
