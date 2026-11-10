package com.argus.modelengine.domain.model;

import com.argus.modelengine.domain.marketdata.MarketEventType;
import com.argus.modelengine.domain.window.WindowDefinition;

/**
 * One input of a model: which kind of market data, seen through which window. Its name is also the
 * instrument slot it leaves open: a model is a template, and each deployment chooses the instrument
 * of every input by its name (ADR 0014, ADR 0017). Immutable, so safe from any thread.
 *
 * @param name how the model and its deployments refer to this input, unique within the model: a
 *     lowercase letter, then lowercase letters, digits or underscores (PEP 8 style, as in the
 *     Python SDK)
 * @param eventType trades or top-of-book updates
 * @param window how the model sees them
 */
public record InputDefinition(String name, MarketEventType eventType, WindowDefinition window) {

  /**
   * Checks the name once, when the model is registered.
   *
   * @throws IllegalArgumentException if the name does not follow the style above
   */
  public InputDefinition {
    boolean valid = !name.isEmpty() && name.charAt(0) >= 'a' && name.charAt(0) <= 'z';
    for (int i = 1; valid && i < name.length(); i++) {
      char c = name.charAt(i);
      valid = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
    }
    if (!valid) {
      throw new IllegalArgumentException(
          "input name must be a lowercase letter, then lowercase letters, digits or underscores: '"
              + name
              + "'");
    }
  }
}
