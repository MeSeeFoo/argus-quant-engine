package com.argus.modelengine.domain.instrument;

/**
 * An instrument as the venue names it ({@code BINANCE:BTCUSDT}). Venue and symbol are uppercase
 * US-ASCII and fit the canonical fixed-length fields, so they are checked once, at startup.
 *
 * @param venue uppercase letters and digits, up to {@link #VENUE_LENGTH}
 * @param symbol uppercase letters and digits, up to {@link #SYMBOL_LENGTH}
 */
public record Instrument(String venue, String symbol) {

  /** Length of the canonical {@code Venue} field. */
  public static final int VENUE_LENGTH = 12;

  /** Length of the canonical {@code Symbol} field. */
  public static final int SYMBOL_LENGTH = 24;

  /**
   * Checks both parts.
   *
   * @throws IllegalArgumentException if either one is not valid
   */
  public Instrument {
    requireCode("venue", venue, VENUE_LENGTH);
    requireCode("symbol", symbol, SYMBOL_LENGTH);
  }

  /**
   * Parses the {@code VENUE:SYMBOL} key used across Argus, for configuration.
   *
   * @param key the key to parse
   * @return the instrument
   * @throws IllegalArgumentException if the key is not {@code VENUE:SYMBOL} or either part is not
   *     valid
   */
  public static Instrument parse(String key) {
    int separator = key.indexOf(':');
    if (separator < 0 || separator != key.lastIndexOf(':')) {
      throw new IllegalArgumentException("instrument must be VENUE:SYMBOL: '" + key + "'");
    }
    return new Instrument(key.substring(0, separator), key.substring(separator + 1));
  }

  @Override
  public String toString() {
    return venue + ":" + symbol;
  }

  /**
   * Checks a venue or symbol once, so no message has to.
   *
   * @param name what the value is, for the error message
   * @param value the value to check
   * @param maxLength the length of its canonical field
   * @throws IllegalArgumentException if it is empty, too long or not uppercase letters and digits
   */
  private static void requireCode(String name, String value, int maxLength) {
    if (value.isEmpty() || value.length() > maxLength) {
      throw new IllegalArgumentException(
          name + " must have 1 to " + maxLength + " characters: '" + value + "'");
    }
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (!(c >= 'A' && c <= 'Z') && !(c >= '0' && c <= '9')) {
        throw new IllegalArgumentException(
            name + " must be uppercase letters and digits: '" + value + "'");
      }
    }
  }
}
