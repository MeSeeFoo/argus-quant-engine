package com.argus.modelengine.domain.window;

/**
 * A window as a model declares it, by number of events (ADR 0029). Immutable, so safe from any
 * thread.
 *
 * @param type sliding or tumbling
 * @param count how many events: the window's length, or a slice's
 * @param slide how many new events between two firings, once the window is full: from 1 (a sliding
 *     window that fires on every event) up to {@code count}. A tumbling window always slides by its
 *     whole count
 */
public record WindowDefinition(Type type, int count, int slide) {

  /** How a window moves. */
  public enum Type {
    /** The last {@code count} events. Fires once full, then every {@code slide} events. */
    SLIDING,
    /** Slices of {@code count} events that do not overlap. Fires when a slice closes. */
    TUMBLING
  }

  /**
   * Checks the count and the slide.
   *
   * @throws IllegalArgumentException if the count is not positive, if the slide is not between 1
   *     and the count, or if a tumbling window does not slide by its count
   */
  public WindowDefinition {
    if (count <= 0) {
      throw new IllegalArgumentException("window count must be positive: " + count);
    }
    if (slide < 1 || slide > count) {
      throw new IllegalArgumentException(
          "window slide must be between 1 and its count " + count + ": " + slide);
    }
    if (type == Type.TUMBLING && slide != count) {
      throw new IllegalArgumentException(
          "a tumbling window slides by its count " + count + ": " + slide);
    }
  }

  /**
   * A sliding window over the last {@code count} events, which fires on every event once full.
   *
   * @param count how many events, positive
   * @return the definition
   */
  public static WindowDefinition sliding(int count) {
    return sliding(count, 1);
  }

  /**
   * A sliding window over the last {@code count} events, which fires once full and then every
   * {@code slide} events. A slide of 10 on a window of 100 runs the model on the last 100 events,
   * every 10 events.
   *
   * @param count how many events, positive
   * @param slide how many new events between two firings, from 1 to {@code count}
   * @return the definition
   */
  public static WindowDefinition sliding(int count, int slide) {
    return new WindowDefinition(Type.SLIDING, count, slide);
  }

  /**
   * A tumbling window of slices of {@code count} events.
   *
   * @param count how many events in a slice, positive
   * @return the definition
   */
  public static WindowDefinition tumbling(int count) {
    return new WindowDefinition(Type.TUMBLING, count, count);
  }
}
