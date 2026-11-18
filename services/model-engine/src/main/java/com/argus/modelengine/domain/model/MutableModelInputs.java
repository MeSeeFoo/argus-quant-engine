package com.argus.modelengine.domain.model;

import com.argus.modelengine.domain.marketdata.BestBidAsk;
import com.argus.modelengine.domain.marketdata.MarketEventType;
import com.argus.modelengine.domain.marketdata.Trade;
import com.argus.modelengine.domain.window.Window;

/**
 * The engine's side of {@link ModelInputs}: the evaluator records which input {@linkplain
 * #fired(int) fired} just before it runs the model. Models only see the read-only interface.
 *
 * <p>It holds the windows' views only, never the windows themselves: what a model reads stays apart
 * from what the engine appends to. Created once per deployment, at startup, by the evaluator. Used
 * only on the evaluator thread. Allocation-free.
 */
public final class MutableModelInputs implements ModelInputs {

  private final Window<?>[] windows;
  private int fired = 0;

  /**
   * Creates the inputs of a deployment. Startup only. Each window knows its input's name and its
   * kind of event.
   *
   * @param windows the window of each input, in the order the model declared them
   */
  public MutableModelInputs(Window<?>[] windows) {
    this.windows = windows;
  }

  @Override
  public Window<?> get(String name) {
    return windows[indexOf(name)];
  }

  @Override
  @SuppressWarnings("unchecked") // Checked: the input reads trades
  public Window<Trade> trades(String name) {
    return (Window<Trade>) windows[indexOf(name, MarketEventType.TRADE)];
  }

  @Override
  @SuppressWarnings("unchecked") // Checked: the input reads top-of-book updates
  public Window<BestBidAsk> bestBidAsks(String name) {
    return (Window<BestBidAsk>) windows[indexOf(name, MarketEventType.BEST_BID_ASK)];
  }

  @Override
  public String fired() {
    return windows[fired].name();
  }

  /**
   * Records which input fired, just before the model runs. Evaluator thread.
   *
   * @param index the position of the input whose window fired, in the order the model declared its
   *     inputs
   */
  public void fired(int index) {
    fired = index;
  }

  /**
   * The position of an input of a given kind of event.
   *
   * @param name the input's name
   * @param eventType the kind of event it must read
   * @return its position
   * @throws IllegalArgumentException if there is no such input, or it reads another kind
   */
  private int indexOf(String name, MarketEventType eventType) {
    int index = indexOf(name);
    if (windows[index].eventType() != eventType) {
      throw new IllegalArgumentException(
          "input '" + name + "' reads " + windows[index].eventType() + ", not " + eventType);
    }
    return index;
  }

  /**
   * The position of an input. A linear scan: a model has a handful of inputs, and comparing names
   * allocates nothing.
   *
   * @param name the input's name
   * @return its position
   * @throws IllegalArgumentException if the model declared no input with that name
   */
  private int indexOf(String name) {
    for (int i = 0; i < windows.length; i++) {
      if (windows[i].name().equals(name)) {
        return i;
      }
    }
    throw new IllegalArgumentException("the model declares no input '" + name + "'");
  }
}
