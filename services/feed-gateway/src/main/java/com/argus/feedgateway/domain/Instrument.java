package com.argus.feedgateway.domain;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * An instrument as the venue names it ({@code BINANCE:BTCUSDT}). Venue and symbol are uppercase
 * US-ASCII and fit the canonical fixed-length fields, so they are checked once here instead of on
 * every message.
 */
public final class Instrument {

  /** Length of the canonical {@code Venue} field. */
  public static final int MAX_VENUE_LENGTH = 12;

  /** Length of the canonical {@code Symbol} field. */
  public static final int MAX_SYMBOL_LENGTH = 24;

  private final String venue;
  private final String symbol;
  private final byte[] symbolBytes;

  public Instrument(String venue, String symbol) {
    this.venue = requireCode("venue", venue, MAX_VENUE_LENGTH);
    this.symbol = requireCode("symbol", symbol, MAX_SYMBOL_LENGTH);
    this.symbolBytes = symbol.getBytes(StandardCharsets.US_ASCII);
  }

  /** Parses the {@code VENUE:SYMBOL} key used across Argus ({@code BINANCE:BTCUSDT}). */
  public static Instrument parse(String key) {
    int separator = key.indexOf(':');
    if (separator < 0 || separator != key.lastIndexOf(':')) {
      throw new IllegalArgumentException("instrument must be VENUE:SYMBOL: '" + key + "'");
    }
    return new Instrument(key.substring(0, separator), key.substring(separator + 1));
  }

  public String venue() {
    return venue;
  }

  public String symbol() {
    return symbol;
  }

  /**
   * Whether the first {@code length} bytes of {@code bytes} are this instrument's symbol, compared
   * without creating a {@code String}.
   */
  public boolean hasSymbol(byte[] bytes, int length) {
    return Arrays.equals(symbolBytes, 0, symbolBytes.length, bytes, 0, length);
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return other instanceof Instrument that
        && venue.equals(that.venue)
        && symbol.equals(that.symbol);
  }

  @Override
  public int hashCode() {
    return 31 * venue.hashCode() + symbol.hashCode();
  }

  @Override
  public String toString() {
    return venue + ":" + symbol;
  }

  private static String requireCode(String name, String value, int maxLength) {
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
    return value;
  }
}
