package com.argus.modelengine.domain;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Every event of one instrument and kind, in arrival order, in pre-allocated objects (ADR 0029).
 *
 * <p>Each event has a <em>position</em>: how many events arrived before it, for as long as the
 * engine runs. The windows of the store are ranges of positions.
 *
 * <ul>
 *   <li>When the store is full and no window needs its oldest event, that object is overwritten.
 *   <li>When a window still needs it, the store doubles, up to its maximum. Growing allocates, but
 *       only until the store reaches the size its windows need.
 *   <li>At the maximum the oldest event is overwritten anyway, and the windows that needed it are
 *       told, so they can report that they are incomplete.
 * </ul>
 *
 * <p>Used only on the evaluator thread, except {@link #lostEvents}, which any thread may read. With
 * its size settled, appending allocates nothing. Not safe to read while appending: windows read
 * events between appends, on the same thread.
 *
 * @param <E> the read-only view of the events it holds
 */
public abstract sealed class EventStore<E extends MarketEvent> permits TradeStore, BestBidAskStore {

  private final Supplier<? extends E> factory;
  private final int maximum;
  private final AtomicLong lostEvents = new AtomicLong();
  private EventWindow<?>[] windows = new EventWindow<?>[0];
  private MarketEvent[] slots;
  // Position of the oldest event held
  private long oldest;
  // Position the next event will take
  private long next;
  // No window needs any position below this one. Windows only move forward, so it stays true
  private long neededFloor;

  /**
   * Allocates the initial objects.
   *
   * @param initial the number of objects allocated at startup, at least 1
   * @param maximum the most objects it may ever hold, at least {@code initial}
   * @param factory creates the store's mutable objects
   * @throws IllegalArgumentException if the sizes are not valid
   */
  EventStore(int initial, int maximum, Supplier<? extends E> factory) {
    if (initial < 1 || maximum < initial) {
      throw new IllegalArgumentException(
          "store sizes must satisfy 1 <= initial <= maximum: " + initial + ", " + maximum);
    }
    this.factory = factory;
    this.maximum = maximum;
    this.slots = new MarketEvent[initial];
    fillEmptySlots(slots);
  }

  /**
   * Creates a sliding window over the last {@code durationNanos} of event time. Startup only.
   *
   * @param durationNanos its length in nanoseconds of event time, positive
   * @return the window, kept up to date by this store
   */
  public final EventWindow<E> slidingTime(long durationNanos) {
    return register(new SlidingTimeWindow<>(this, durationNanos));
  }

  /**
   * Creates a sliding window over the last {@code count} events. Startup only.
   *
   * @param count how many events, positive
   * @return the window, kept up to date by this store
   */
  public final EventWindow<E> slidingCount(int count) {
    return register(new SlidingCountWindow<>(this, count));
  }

  /**
   * Creates a tumbling window of slices of {@code durationNanos} of event time, aligned to the
   * epoch. Startup only.
   *
   * @param durationNanos the length of a slice in nanoseconds of event time, positive
   * @return the window, kept up to date by this store
   */
  public final TumblingTimeWindow<E> tumblingTime(long durationNanos) {
    return register(new TumblingTimeWindow<>(this, durationNanos));
  }

  /**
   * Creates a tumbling window of slices of {@code count} events. Startup only.
   *
   * @param count how many events in a slice, positive
   * @return the window, kept up to date by this store
   */
  public final TumblingWindow<E> tumblingCount(int count) {
    return register(new TumblingCountWindow<>(this, count));
  }

  /**
   * Adds a window that this store keeps up to date.
   *
   * @param window the new window
   * @param <W> its type
   * @return {@code window}
   * @throws IllegalArgumentException if the window could never fit in this store
   */
  private <W extends EventWindow<E>> W register(W window) {
    if (window.minimumCapacity() > maximum) {
      throw new IllegalArgumentException(
          "window needs "
              + window.minimumCapacity()
              + " events but the store's maximum is "
              + maximum);
    }
    windows = Arrays.copyOf(windows, windows.length + 1);
    windows[windows.length - 1] = window;
    return window;
  }

  /**
   * Moves every window forward and frees a slot for a new event. Evaluator thread. Allocation-free
   * unless the store grows.
   *
   * @param timestamp the new event's {@code exchangeTimestamp}, in epoch nanoseconds
   * @return the object to overwrite with the new event
   */
  final E claim(long timestamp) {
    long position = next;
    for (int i = 0; i < windows.length; i++) {
      windows[i].onAppend(position, timestamp);
    }
    if (position - oldest == slots.length) {
      makeRoom();
    }
    next = position + 1;
    return at(position);
  }

  /** Frees the slot of the oldest event, or adds slots if a window still needs it. */
  private void makeRoom() {
    if (oldest >= neededFloor) {
      neededFloor = firstNeeded();
    }
    if (oldest < neededFloor) {
      oldest++;
    } else if (slots.length < maximum) {
      grow();
    } else {
      lose();
    }
  }

  /**
   * The oldest position any window needs.
   *
   * @return that position, or {@link Long#MAX_VALUE} without windows
   */
  private long firstNeeded() {
    long first = Long.MAX_VALUE;
    for (int i = 0; i < windows.length; i++) {
      first = Math.min(first, windows[i].firstNeeded());
    }
    return first;
  }

  /** Doubles the store up to its maximum, keeping the same objects at their positions. */
  private void grow() {
    int capacity = (int) Math.min(slots.length * 2L, maximum);
    MarketEvent[] grown = new MarketEvent[capacity];
    for (long position = oldest; position < next; position++) {
      grown[(int) (position % capacity)] = slots[(int) (position % slots.length)];
    }
    fillEmptySlots(grown);
    slots = grown;
  }

  /** Overwrites the oldest event although a window needs it, and tells the windows that do. */
  private void lose() {
    long timestamp = timestampAt(oldest);
    for (int i = 0; i < windows.length; i++) {
      if (windows[i].firstNeeded() <= oldest) {
        windows[i].onLost(oldest, timestamp);
      }
    }
    lostEvents.incrementAndGet();
    oldest++;
  }

  /**
   * Allocates an object for every empty slot.
   *
   * @param target the slots to fill
   */
  private void fillEmptySlots(MarketEvent[] target) {
    for (int i = 0; i < target.length; i++) {
      if (target[i] == null) {
        target[i] = factory.get();
      }
    }
  }

  /**
   * The event at a position, which must still be held. Evaluator thread. Allocation-free.
   *
   * @param position the event's position
   * @return its object, valid until the store reuses it
   */
  @SuppressWarnings("unchecked") // Every slot is created by the factory of E
  final E at(long position) {
    return (E) slots[(int) (position % slots.length)];
  }

  /**
   * The event time of the event at a position, which must still be held. Allocation-free.
   *
   * @param position the event's position
   * @return its {@code exchangeTimestamp}, in epoch nanoseconds
   */
  final long timestampAt(long position) {
    return slots[(int) (position % slots.length)].exchangeTimestamp();
  }

  /**
   * The position the next event will take. Evaluator thread.
   *
   * @return that position
   */
  final long nextPosition() {
    return next;
  }

  /**
   * How many objects the store has allocated. Evaluator thread.
   *
   * @return its current capacity
   */
  public final int capacity() {
    return slots.length;
  }

  /**
   * Events overwritten at the maximum size although a window needed them. Any thread.
   *
   * @return the count since the engine started
   */
  public final long lostEvents() {
    return lostEvents.get();
  }
}
