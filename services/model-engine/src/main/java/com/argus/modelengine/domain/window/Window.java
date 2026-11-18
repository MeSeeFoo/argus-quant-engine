package com.argus.modelengine.domain.window;

import com.argus.modelengine.domain.marketdata.MarketEvent;
import com.argus.modelengine.domain.marketdata.MarketEventType;

/**
 * What a model receives when one of its windows fires: the window's events, oldest first in arrival
 * order, and its event-time "now" (ADR 0029). Record-style accessors, but a class: each window
 * creates its view once and refills it when it fires, so handing it to a model allocates nothing,
 * which a record, immutable, could not do.
 *
 * <p>The view of a window is always the same object, so a model with several inputs may keep the
 * views of its other windows and read them when one fires. What they hold is valid only during the
 * evaluation, on the evaluator thread: the next event changes it. So are the events in the array.
 * The array must not be modified.
 *
 * @param <E> the read-only view of the events it holds: {@code Trade} or {@code BestBidAsk}
 */
public final class Window<E extends MarketEvent> {

  /** {@link #now} before the first event: no event has that timestamp. */
  public static final long NO_TIMESTAMP = Long.MIN_VALUE;

  private final String name;
  private final MarketEventType eventType;
  private final WindowDefinition.Type windowType;
  private E[] events;
  private long now = NO_TIMESTAMP;

  /**
   * Creates the view of a window. Startup only.
   *
   * @param name the name of the model's input this window serves
   * @param eventType the kind of event it holds
   * @param windowType whether it slides or tumbles
   * @param events what it holds before it first fires: an empty array
   */
  Window(String name, MarketEventType eventType, WindowDefinition.Type windowType, E[] events) {
    this.name = name;
    this.eventType = eventType;
    this.windowType = windowType;
    this.events = events;
  }

  /**
   * The name of the model's input this window serves.
   *
   * @return the input's name, as the model declared it
   */
  public String name() {
    return name;
  }

  /**
   * The kind of event this window holds, which says the type of {@link #events}.
   *
   * @return {@code TRADE} for trades, {@code BEST_BID_ASK} for top-of-book updates
   */
  public MarketEventType eventType() {
    return eventType;
  }

  /**
   * Whether this window slides or tumbles.
   *
   * @return its type, as the model declared it
   */
  public WindowDefinition.Type windowType() {
    return windowType;
  }

  /**
   * The window's events, oldest first. As many as the window's count once it has fired, since a
   * window fires only when it is full; empty before that, never with {@code null} entries.
   * Allocation-free.
   *
   * @return the array, valid only during the current evaluation; never modify it
   */
  public E[] events() {
    return events;
  }

  /**
   * The window's "now": the latest {@code exchangeTimestamp} it was given, never the system clock
   * (ADR 0017). Never goes backwards. For a tumbling window it may be newer than its last closed
   * slice.
   *
   * @return epoch nanoseconds, or {@link #NO_TIMESTAMP} before the first event
   */
  public long now() {
    return now;
  }

  /**
   * Points the view at the events the window just fired with.
   *
   * @param fired the window's events, oldest first
   */
  void events(E[] fired) {
    events = fired;
  }

  /**
   * Moves "now" forward with an event.
   *
   * @param timestamp the event's {@code exchangeTimestamp}, in epoch nanoseconds
   */
  void advance(long timestamp) {
    now = Math.max(now, timestamp);
  }
}
