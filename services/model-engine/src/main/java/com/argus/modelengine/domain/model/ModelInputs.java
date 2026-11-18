package com.argus.modelengine.domain.model;

import com.argus.modelengine.domain.marketdata.BestBidAsk;
import com.argus.modelengine.domain.marketdata.Trade;
import com.argus.modelengine.domain.window.Window;

/**
 * What a model receives each time one of its windows fires: the windows of all its inputs, by name,
 * and which one fired. It holds all of them, not only the one that fired, so the model reads its
 * other inputs as they are at that moment (ADR 0017, ADR 0029).
 *
 * <p>Read-only: the engine's {@link MutableModelInputs} is the only implementation, created once
 * per deployment, so evaluating a model allocates nothing. Used only on the evaluator thread; what
 * the windows hold is valid only during the evaluation.
 */
public interface ModelInputs {

  /**
   * The window of an input, for a model that does not need to know its kind of event.
   * Allocation-free.
   *
   * @param name the input's name, as the model declared it
   * @return its window, valid only during the current evaluation
   * @throws IllegalArgumentException if the model declared no input with that name
   */
  Window<?> get(String name);

  /**
   * The window of an input of trades. Allocation-free.
   *
   * @param name the input's name, as the model declared it
   * @return its window, valid only during the current evaluation
   * @throws IllegalArgumentException if there is no such input, or it is not of trades
   */
  Window<Trade> trades(String name);

  /**
   * The window of an input of top-of-book updates. Allocation-free.
   *
   * @param name the input's name, as the model declared it
   * @return its window, valid only during the current evaluation
   * @throws IllegalArgumentException if there is no such input, or it is not of top-of-book updates
   */
  Window<BestBidAsk> bestBidAsks(String name);

  /**
   * Which input fired this evaluation.
   *
   * @return its name
   */
  String fired();
}
