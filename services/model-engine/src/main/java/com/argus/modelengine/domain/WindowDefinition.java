package com.argus.modelengine.domain;

/**
 * A window as a model declares it, by number of events (ADR 0029). Immutable, so safe from any
 * thread.
 *
 * @param type sliding or tumbling
 * @param count how many events: the window's length, or a slice's
 */
public record WindowDefinition(Type type, int count) {

  /** How a window moves. */
  public enum Type {
    /** The last {@code count} events. Fires on every event. */
    SLIDING,
    /** Slices of {@code count} events that do not overlap. Fires when a slice closes. */
    TUMBLING
  }

  /**
   * Checks the count.
   *
   * @throws IllegalArgumentException if the count is not positive
   */
  public WindowDefinition {
    if (count <= 0) {
      throw new IllegalArgumentException("window count must be positive: " + count);
    }
  }

  /**
   * A sliding window over the last {@code count} events.
   *
   * @param count how many events, positive
   * @return the definition
   */
  public static WindowDefinition sliding(int count) {
    return new WindowDefinition(Type.SLIDING, count);
  }

  /**
   * A tumbling window of slices of {@code count} events.
   *
   * @param count how many events in a slice, positive
   * @return the definition
   */
  public static WindowDefinition tumbling(int count) {
    return new WindowDefinition(Type.TUMBLING, count);
  }

  /**
   * How many events a store must keep for this window: {@code count} for a sliding window, and
   * {@code 2 × count − 1} for a tumbling one, whose last closed slice stays readable while the next
   * one fills.
   *
   * @return the number of events
   */
  public int requiredCapacity() {
    return type == Type.SLIDING ? count : 2 * count - 1;
  }
}
