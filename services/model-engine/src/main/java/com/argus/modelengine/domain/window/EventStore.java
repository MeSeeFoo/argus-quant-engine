package com.argus.modelengine.domain.window;

import com.argus.modelengine.domain.marketdata.MarketEvent;
import java.util.Arrays;
import java.util.function.Supplier;

/**
 * The latest events of one instrument and kind, in arrival order, in pre-allocated objects (ADR
 * 0029).
 *
 * <p>Its capacity is fixed at startup to what its windows need, so it never grows. When it is full
 * the oldest object is overwritten, which no window needs any more: a window that would need more
 * than the capacity is rejected when it is created.
 *
 * <p>Each event has a <em>position</em>: how many events arrived before it, for as long as the
 * engine runs. The windows of the store are ranges of positions.
 *
 * <p>Used only on the evaluator thread. Appending allocates nothing. Not safe to read while
 * appending: windows read events between appends, on the same thread.
 *
 * @param <E> the read-only view of the events it holds
 */
public abstract sealed class EventStore<E extends MarketEvent> permits TradeStore, BestBidAskStore {

  private final MarketEvent[] slots;
  private EventWindow<?>[] windows = new EventWindow<?>[0];
  // Position the next event will take
  private long next;

  /**
   * Allocates every object the store will ever use.
   *
   * @param capacity how many events it keeps, at least 1
   * @param factory creates the store's mutable objects
   * @throws IllegalArgumentException if the capacity is not positive
   */
  EventStore(int capacity, Supplier<? extends E> factory) {
    if (capacity < 1) {
      throw new IllegalArgumentException("store capacity must be positive: " + capacity);
    }
    slots = new MarketEvent[capacity];
    for (int i = 0; i < capacity; i++) {
      slots[i] = factory.get();
    }
  }

  /**
   * Creates a window that this store keeps up to date. Startup only.
   *
   * @param definition what the model declared
   * @return the window, starting with the store's next event
   * @throws IllegalArgumentException if the window needs more events than this store keeps
   */
  public final EventWindow<E> window(WindowDefinition definition) {
    if (definition.requiredCapacity() > slots.length) {
      throw new IllegalArgumentException(
          "window "
              + definition
              + " needs "
              + definition.requiredCapacity()
              + " events but the store keeps "
              + slots.length);
    }
    EventWindow<E> window =
        switch (definition.type()) {
          case SLIDING -> new SlidingWindow<>(this, definition.count());
          case TUMBLING -> new TumblingWindow<>(this, definition.count());
        };
    windows = Arrays.copyOf(windows, windows.length + 1);
    windows[windows.length - 1] = window;
    return window;
  }

  /**
   * Moves every window forward and returns the object for a new event: the oldest one once the
   * store is full. Evaluator thread. Allocation-free.
   *
   * @param timestamp the new event's {@code exchangeTimestamp}, in epoch nanoseconds
   * @return the object to overwrite with the new event
   */
  final E claim(long timestamp) {
    long position = next;
    for (int i = 0; i < windows.length; i++) {
      windows[i].onAppend(position, timestamp);
    }
    next = position + 1;
    return at(position);
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
   * The position the next event will take. Evaluator thread.
   *
   * @return that position
   */
  final long nextPosition() {
    return next;
  }

  /**
   * How many events the store keeps.
   *
   * @return its capacity, fixed at startup
   */
  public final int capacity() {
    return slots.length;
  }
}
