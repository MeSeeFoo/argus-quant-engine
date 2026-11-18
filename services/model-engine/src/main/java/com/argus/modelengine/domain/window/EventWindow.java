package com.argus.modelengine.domain.window;

import com.argus.modelengine.domain.marketdata.BestBidAsk;
import com.argus.modelengine.domain.marketdata.MarketEvent;
import com.argus.modelengine.domain.marketdata.MarketEventType;
import com.argus.modelengine.domain.marketdata.MutableBestBidAsk;
import com.argus.modelengine.domain.marketdata.MutableTrade;
import com.argus.modelengine.domain.marketdata.Trade;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * A window of one instrument and kind of event, as one model declared it, by number of events (ADR
 * 0029). The evaluator {@linkplain #append appends} each event of that instrument and kind, and
 * hands the {@link Window} to its model when it fires.
 *
 * <p>The window keeps <b>copies</b> of its events, in objects it allocates once, when it is
 * created: what it is given is a slot of the Disruptor ring, which the ring overwrites as soon as
 * the evaluator moves on. Events stay in arrival order and are never reordered, so a replay gives
 * the same result as the original run.
 *
 * <p>Used only on the evaluator thread. Appending allocates nothing.
 *
 * @param <E> the read-only view of the events it holds: {@link Trade} or {@link BestBidAsk}
 */
public abstract sealed class EventWindow<E extends MarketEvent>
    permits SlidingWindow, TumblingWindow {

  /** The window's length, or a slice's, in events. */
  final int count;

  /** What the model receives; always the same object. */
  final Window<E> view;

  private final IntFunction<E[]> arrays;
  private final Supplier<? extends E> factory;
  private final Copier<E> copier;

  /**
   * Creates the window and its view.
   *
   * @param name the name of the model's input this window serves
   * @param eventType the kind of event it holds
   * @param definition what the model declared
   * @param arrays creates arrays of the event's own type, so a model can read them as such
   * @param factory creates the mutable copies
   * @param copier copies an event into one of them
   */
  EventWindow(
      String name,
      MarketEventType eventType,
      WindowDefinition definition,
      IntFunction<E[]> arrays,
      Supplier<? extends E> factory,
      Copier<E> copier) {
    this.count = definition.count();
    this.arrays = arrays;
    this.factory = factory;
    this.copier = copier;
    this.view = new Window<>(name, eventType, definition.type(), arrays.apply(0));
  }

  /**
   * Creates a window of a kind of event known only at runtime, such as a model input's. Startup
   * only. A new kind of event adds its case here.
   *
   * @param name the name of the model's input it serves
   * @param eventType the kind of event it holds
   * @param definition what the model declared
   * @return the empty window
   */
  public static EventWindow<?> of(
      String name, MarketEventType eventType, WindowDefinition definition) {
    return switch (eventType) {
      case TRADE -> ofTrades(name, definition);
      case BEST_BID_ASK -> ofBestBidAsks(name, definition);
    };
  }

  /**
   * Creates a window of trades. Startup only.
   *
   * @param name the name of the model's input it serves
   * @param definition what the model declared
   * @return the empty window
   */
  public static EventWindow<Trade> ofTrades(String name, WindowDefinition definition) {
    return create(
        name,
        MarketEventType.TRADE,
        definition,
        Trade[]::new,
        MutableTrade::new,
        EventWindow::copyTrade);
  }

  /**
   * Creates a window of top-of-book updates. Startup only.
   *
   * @param name the name of the model's input it serves
   * @param definition what the model declared
   * @return the empty window
   */
  public static EventWindow<BestBidAsk> ofBestBidAsks(String name, WindowDefinition definition) {
    return create(
        name,
        MarketEventType.BEST_BID_ASK,
        definition,
        BestBidAsk[]::new,
        MutableBestBidAsk::new,
        EventWindow::copyBestBidAsk);
  }

  /**
   * Creates the window of a definition's type.
   *
   * @param name the name of the model's input it serves
   * @param eventType the kind of event it holds
   * @param definition what the model declared
   * @param arrays creates arrays of the event's own type
   * @param factory creates the mutable copies
   * @param copier copies an event into one of them
   * @param <E> the kind of event
   * @return the empty window
   */
  private static <E extends MarketEvent> EventWindow<E> create(
      String name,
      MarketEventType eventType,
      WindowDefinition definition,
      IntFunction<E[]> arrays,
      Supplier<? extends E> factory,
      Copier<E> copier) {
    return switch (definition.type()) {
      case SLIDING -> new SlidingWindow<>(name, eventType, definition, arrays, factory, copier);
      case TUMBLING -> new TumblingWindow<>(name, eventType, definition, arrays, factory, copier);
    };
  }

  /**
   * Adds an event and tells whether the window fires: a sliding window on every event once it is
   * full, a tumbling window when a slice closes. Evaluator thread. Allocation-free.
   *
   * @param event the event, copied, so valid only during this call
   * @return the view, to hand to the window's model, if it fires; {@code null} otherwise
   */
  public final @Nullable Window<E> append(E event) {
    view.advance(event.exchangeTimestamp());
    return add(event) ? view : null;
  }

  /**
   * The view that the window's model receives. Startup only: for a model that reads this window
   * when another of its windows fires.
   *
   * @return the view, always the same object
   */
  public final Window<E> view() {
    return view;
  }

  /**
   * Copies an event into the window, moves it forward and, if it fires, fills the view.
   * Allocation-free.
   *
   * @param event the event, valid only during this call
   * @return {@code true} if the window fires for this event
   */
  abstract boolean add(E event);

  /**
   * Allocates an array of {@code count} events. Startup only.
   *
   * @param withCopies {@code true} to fill it with new mutable copies, {@code false} to leave it
   *     for references to the window's copies
   * @return the array
   */
  final E[] newArray(boolean withCopies) {
    E[] array = arrays.apply(count);
    for (int i = 0; withCopies && i < count; i++) {
      array[i] = factory.get();
    }
    return array;
  }

  /**
   * Copies an event into one of the window's own objects.
   *
   * @param event the event, valid only during this call
   * @param into the copy to overwrite
   */
  final void copy(E event, E into) {
    copier.copy(event, into);
  }

  /**
   * Copies a trade into one of a window's own objects.
   *
   * @param from the trade, valid only during the call
   * @param into a copy created by {@code MutableTrade::new}
   */
  private static void copyTrade(Trade from, Trade into) {
    ((MutableTrade) into).copyFrom(from);
  }

  /**
   * Copies a top-of-book update into one of a window's own objects.
   *
   * @param from the update, valid only during the call
   * @param into a copy created by {@code MutableBestBidAsk::new}
   */
  private static void copyBestBidAsk(BestBidAsk from, BestBidAsk into) {
    ((MutableBestBidAsk) into).copyFrom(from);
  }

  /**
   * Copies an event into one of a window's own objects, without allocating.
   *
   * @param <E> the kind of event
   */
  @FunctionalInterface
  interface Copier<E> {

    /**
     * Overwrites {@code into} with {@code from}.
     *
     * @param from the event to copy, valid only during the call
     * @param into one of the window's own objects
     */
    void copy(E from, E into);
  }
}
