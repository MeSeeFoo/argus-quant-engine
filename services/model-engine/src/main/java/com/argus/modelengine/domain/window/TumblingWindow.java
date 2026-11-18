package com.argus.modelengine.domain.window;

import com.argus.modelengine.domain.marketdata.MarketEvent;
import com.argus.modelengine.domain.marketdata.MarketEventType;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Slices of {@code count} events that do not overlap. A slice closes, and the window fires, with
 * its last event; the view then holds that slice until the next one closes.
 *
 * <p>It keeps two arrays of {@code count} copies: one is the last closed slice, which the view
 * points at, and the other fills up with the next one. When a slice closes they swap, so the closed
 * slice stays readable while the next one fills, for a model that reads it when another of its
 * windows fires. Firing copies nothing.
 *
 * @param <E> the read-only view of the events it holds
 */
final class TumblingWindow<E extends MarketEvent> extends EventWindow<E> {

  private E[] open;
  private E[] closed;
  private int openSize = 0;

  /**
   * Creates a window with no slices.
   *
   * @param name the name of the model's input it serves
   * @param eventType the kind of event it holds
   * @param definition what the model declared: a tumbling window of a positive count
   * @param arrays creates arrays of the event's own type, so a model can read them as such
   * @param factory creates the mutable copies
   * @param copier copies an event into one of them
   */
  TumblingWindow(
      String name,
      MarketEventType eventType,
      WindowDefinition definition,
      IntFunction<E[]> arrays,
      Supplier<? extends E> factory,
      Copier<E> copier) {
    super(name, eventType, definition, arrays, factory, copier);
    this.open = newArray(true);
    this.closed = newArray(true);
  }

  @Override
  boolean add(E event) {
    copy(event, open[openSize]);
    openSize++;
    if (openSize < count) {
      return false;
    }
    E[] justClosed = open;
    open = closed;
    closed = justClosed;
    openSize = 0;
    view.events(closed);
    return true;
  }
}
