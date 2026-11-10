package com.argus.modelengine.domain.model;

import com.argus.modelengine.domain.marketdata.MarketEventType;
import com.argus.modelengine.domain.window.WindowDefinition;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a model declares about itself, built with a {@link Builder} and read once, when the model is
 * registered (ADR 0014). From it the engine works out what to keep and when to run the model (ADR
 * 0029). For example, a model that wants the top of book of one instrument in slices of 100
 * updates:
 *
 * <pre>{@code
 * ModelConfiguration.builder()
 *     .input("quotes", "asset", MarketEventType.BEST_BID_ASK, WindowDefinition.tumbling(100))
 *     .build();
 * }</pre>
 *
 * <p>Immutable, so safe from any thread. It is plain data, so it can be stored and read without
 * running the model's code.
 *
 * @param inputs the model's inputs, at least one, with unique names, in declaration order
 */
public record ModelConfiguration(List<InputDefinition> inputs) {

  /**
   * Copies the inputs and checks them.
   *
   * @throws IllegalArgumentException if there are no inputs or two share a name
   * @throws NullPointerException if an input is {@code null}
   */
  public ModelConfiguration {
    inputs = List.copyOf(inputs);
    if (inputs.isEmpty()) {
      throw new IllegalArgumentException("a model needs at least one input");
    }
    Set<String> names = new HashSet<>();
    for (InputDefinition input : inputs) {
      if (!names.add(input.name())) {
        throw new IllegalArgumentException("two inputs are named '" + input.name() + "'");
      }
    }
  }

  /**
   * Starts a configuration.
   *
   * @return an empty builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * The instrument slots the inputs use: what a deployment has to fill. Several inputs may read the
   * same slot, such as its trades and its top of book.
   *
   * @return each slot once, in the order its first input was declared
   */
  public List<String> instrumentSlots() {
    Set<String> slots = new LinkedHashSet<>();
    for (InputDefinition input : inputs) {
      slots.add(input.instrumentSlot());
    }
    return List.copyOf(slots);
  }

  /**
   * Builds a {@link ModelConfiguration}. Not thread-safe; a model uses it once, when it is
   * registered. Each {@link #build} returns its own copy, so the builder may go on afterwards.
   */
  public static final class Builder {

    private final List<InputDefinition> inputs = new ArrayList<>();

    /** Created by {@link ModelConfiguration#builder}. */
    private Builder() {}

    /**
     * Adds an input.
     *
     * @param name how the model refers to it, unique within the model
     * @param instrumentSlot the slot whose instrument it reads, filled when deploying
     * @param eventType trades or top-of-book updates
     * @param window how the model sees them
     * @return this builder
     * @throws IllegalArgumentException if a name does not follow {@link InputDefinition}'s style
     */
    public Builder input(
        String name, String instrumentSlot, MarketEventType eventType, WindowDefinition window) {
      inputs.add(new InputDefinition(name, instrumentSlot, eventType, window));
      return this;
    }

    /**
     * Builds the configuration with the inputs added so far.
     *
     * @return the configuration
     * @throws IllegalArgumentException if there are no inputs or two share a name
     */
    public ModelConfiguration build() {
      return new ModelConfiguration(inputs);
    }
  }
}
