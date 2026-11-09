package com.argus.modelengine.domain;

import java.util.Objects;

/**
 * A window cut into slices that do not overlap. {@link #size} and {@link #get} read the last closed
 * slice, which never changes until the next one closes. The slice still filling up is read with
 * {@link #openSize} and {@link #getOpen}. A slice with no events is never created.
 *
 * <p>Used only on the evaluator thread, and read between appends to its store. Reading allocates
 * nothing.
 *
 * @param <E> the read-only view of the events it holds
 */
public abstract sealed class TumblingWindow<E extends MarketEvent> extends EventWindow<E>
    permits TumblingTimeWindow, TumblingCountWindow {

  // The open slice: positions openFirst (inclusive) to openEnd (exclusive)
  long openFirst;
  long openEnd;

  private boolean openLost;
  private boolean closedLost;
  private long closedSlices;

  /**
   * Creates a window with no slices.
   *
   * @param store the store it reads
   */
  TumblingWindow(EventStore<E> store) {
    super(store);
    this.openFirst = first;
    this.openEnd = first;
  }

  /**
   * Makes the open slice the closed one, and opens a new one at {@code position}. Allocation-free.
   *
   * @param position the first position after the slice being closed
   */
  final void closeSlice(long position) {
    first = openFirst;
    end = position;
    closedLost = openLost;
    openFirst = position;
    openLost = false;
    closedSlices++;
  }

  @Override
  final long firstNeeded() {
    return first < end ? first : openFirst;
  }

  @Override
  final void onLost(long position, long timestamp) {
    if (position < end) {
      first = position + 1;
      closedLost = true;
    } else {
      openFirst = position + 1;
      openLost = true;
    }
  }

  /**
   * Whether the closed slice holds all its events.
   *
   * @return {@code true} if none of its events was lost
   */
  @Override
  public final boolean isComplete() {
    return !closedLost;
  }

  /**
   * How many slices have closed. A model evaluated more often than slices close uses it to tell
   * when there is a new one.
   *
   * @return the count since the window was created
   */
  public final long closedSlices() {
    return closedSlices;
  }

  /**
   * How many events the slice still filling up holds.
   *
   * @return the number of events
   */
  public final int openSize() {
    return (int) (openEnd - openFirst);
  }

  /**
   * An event of the slice still filling up, oldest first in arrival order. Allocation-free.
   *
   * @param index from 0 to {@link #openSize} (exclusive)
   * @return the event, valid only during the current evaluation
   * @throws IndexOutOfBoundsException if {@code index} is out of range
   */
  public final E getOpen(int index) {
    return store.at(openFirst + Objects.checkIndex(index, openSize()));
  }
}
