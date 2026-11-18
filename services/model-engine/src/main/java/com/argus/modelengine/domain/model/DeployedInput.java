package com.argus.modelengine.domain.model;

import com.argus.modelengine.domain.instrument.Instrument;
import com.argus.modelengine.domain.marketdata.MarketEventType;

/**
 * One input of a deployment: what the model declared for it, joined with the instrument the
 * deployment chose. The model's {@link InputDefinition} is a template shared by every deployment of
 * the model, so the instrument goes here, never into it (ADR 0014). Built at startup by {@link
 * DeployedModel#deploy}. Immutable, so safe from any thread.
 *
 * @param definition what the model declared: the input's name, kind of event and window
 * @param instrument the instrument the deployment chose for it
 * @param instrumentId the instrument's id in the engine's {@code Instruments}, for the hot path
 */
public record DeployedInput(InputDefinition definition, Instrument instrument, int instrumentId) {

  /**
   * Checks the id.
   *
   * @throws IllegalArgumentException if the instrument id is negative
   */
  public DeployedInput {
    if (instrumentId < 0) {
      throw new IllegalArgumentException("instrument id must not be negative: " + instrumentId);
    }
  }

  /**
   * The input's name, as the model declared it.
   *
   * @return the name
   */
  public String name() {
    return definition.name();
  }

  /**
   * The kind of event the input reads.
   *
   * @return trades or top-of-book updates
   */
  public MarketEventType eventType() {
    return definition.eventType();
  }
}
