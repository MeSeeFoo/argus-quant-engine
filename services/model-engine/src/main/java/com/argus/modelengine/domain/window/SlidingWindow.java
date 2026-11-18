package com.argus.modelengine.domain.window;

import com.argus.modelengine.domain.marketdata.MarketEvent;
import com.argus.modelengine.domain.marketdata.MarketEventType;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * The last {@code count} events. Fires once it holds {@code count} of them, then every {@code
 * slide} events: a slide of 1 fires on every event, so a sliding window of one event is "every
 * update".
 *
 * <p>Its copies form a circular array of {@code count} objects, where each new event overwrites the
 * oldest. On every event once full, it lines up references to them oldest first in a second array,
 * which the view points at: {@code count} references copied per event, no allocation. It does so
 * even when it does not fire, because the event just added overwrote one of the copies the view
 * pointed at: a model with several inputs reads this one when another fires, and must see the last
 * {@code count} events in order, never a mix.
 *
 * @param <E> the read-only view of the events it holds
 */
final class SlidingWindow<E extends MarketEvent> extends EventWindow<E> {

  private final E[] copies;
  // The copies oldest first, which the view points at once the window is full
  private final E[] ordered;
  // How many new events between two firings, once full
  private final int slide;
  // Index of the copy the next event overwrites: the oldest one once the window is full
  private int next = 0;
  private int held = 0;
  // Events still to come before the next firing; 0 fires on the next event once full
  private int untilFiring = 0;

  /**
   * Creates an empty window.
   *
   * @param name the name of the model's input it serves
   * @param eventType the kind of event it holds
   * @param definition what the model declared: a sliding window of a positive count and a slide
   * @param arrays creates arrays of the event's own type, so a model can read them as such
   * @param factory creates the mutable copies
   * @param copier copies an event into one of them
   */
  SlidingWindow(
      String name,
      MarketEventType eventType,
      WindowDefinition definition,
      IntFunction<E[]> arrays,
      Supplier<? extends E> factory,
      Copier<E> copier) {
    super(name, eventType, definition, arrays, factory, copier);
    this.copies = newArray(true);
    this.ordered = newArray(false);
    this.slide = definition.slide();
  }

  @Override
  boolean add(E event) {
    copy(event, copies[next]);
    next = next + 1 == count ? 0 : next + 1;
    if (held < count) {
      held++;
    }
    if (held < count) {
      return false;
    }
    // Oldest first: the oldest copy is the one the next event will overwrite
    for (int i = 0; i < count; i++) {
      int position = next + i;
      ordered[i] = copies[position < count ? position : position - count];
    }
    view.events(ordered);
    if (untilFiring > 0) {
      untilFiring--;
      return false;
    }
    untilFiring = slide - 1;
    return true;
  }
}
