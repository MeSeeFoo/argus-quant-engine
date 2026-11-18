package com.argus.modelengine.domain.model;

/**
 * A model the engine runs: it declares its inputs once, and is evaluated each time one of their
 * windows fires (ADR 0014, ADR 0029). Its outputs arrive with the model contract (#112).
 */
public interface Model {

  /**
   * What the model declares about itself. Read once, when the model is registered.
   *
   * @return its configuration
   */
  ModelConfiguration modelConfiguration();

  /**
   * Evaluates the model, on the evaluator thread, each time one of its windows fires. It must not
   * block or allocate in steady state, and should not throw: a failure is counted and the engine
   * carries on with the next event.
   *
   * @param inputs the windows of all its inputs, and which one fired; valid only during this call
   */
  void evaluate(ModelInputs inputs);
}
