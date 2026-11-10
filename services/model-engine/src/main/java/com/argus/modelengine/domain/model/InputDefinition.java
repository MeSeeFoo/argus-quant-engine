package com.argus.modelengine.domain.model;

import com.argus.modelengine.domain.marketdata.MarketEventType;
import com.argus.modelengine.domain.window.WindowDefinition;

/**
 * One input of a model: which kind of market data, of which instrument slot, seen through which
 * window. The instrument itself is chosen when the model is deployed: a model is a template, and a
 * slot is the name of the instrument it leaves open (ADR 0014, ADR 0017). Immutable, so safe from
 * any thread.
 *
 * @param name how the model refers to this input, unique within the model: a lowercase letter, then
 *     lowercase letters, digits or underscores (PEP 8 style, as in the Python SDK)
 * @param instrumentSlot the slot whose instrument this input reads, in the same style
 * @param eventType trades or top-of-book updates
 * @param window how the model sees them
 */
public record InputDefinition(
    String name, String instrumentSlot, MarketEventType eventType, WindowDefinition window) {

  /**
   * Checks the names.
   *
   * @throws IllegalArgumentException if a name does not follow the style above
   */
  public InputDefinition {
    requireIdentifier("input name", name);
    requireIdentifier("instrument slot", instrumentSlot);
  }

  /**
   * Checks a name once, when the model is registered.
   *
   * @param what what the name is, for the error message
   * @param value the name to check
   * @throws IllegalArgumentException if it is not a lowercase identifier
   */
  static void requireIdentifier(String what, String value) {
    boolean valid = !value.isEmpty() && value.charAt(0) >= 'a' && value.charAt(0) <= 'z';
    for (int i = 1; valid && i < value.length(); i++) {
      char c = value.charAt(i);
      valid = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
    }
    if (!valid) {
      throw new IllegalArgumentException(
          what
              + " must be a lowercase letter, then lowercase letters, digits or underscores: '"
              + value
              + "'");
    }
  }
}
