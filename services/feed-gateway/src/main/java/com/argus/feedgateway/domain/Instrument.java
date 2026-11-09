package com.argus.feedgateway.domain;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * An instrument as the venue names it ({@code BINANCE:BTCUSDT}). Venue and symbol are uppercase
 * US-ASCII and fit the canonical fixed-length fields, so they are checked once here instead of on
 * every message.
 *
 * <p>The instruments of the shard come from {@link Instruments}, which gives each one its {@link
 * #id}: the hot path indexes per-instrument state with it instead of looking the instrument up.
 */
public final class Instrument {

  /** Length of the canonical {@code Venue} field. */
  public static final int MAX_VENUE_LENGTH = 12;

  /** Length of the canonical {@code Symbol} field. */
  public static final int MAX_SYMBOL_LENGTH = 24;

  private final String venue;
  private final String symbol;
  private final byte[] symbolBytes;
  private final int id;

  /**
   * An instrument outside the shard's {@link Instruments}: its id is {@link Instruments#UNKNOWN}.
   *
   * @param venue uppercase letters and digits, up to {@link #MAX_VENUE_LENGTH}
   * @param symbol uppercase letters and digits, up to {@link #MAX_SYMBOL_LENGTH}
   * @throws IllegalArgumentException if either one is not valid
   */
  public Instrument(String venue, String symbol) {
    this.venue = requireCode("venue", venue, MAX_VENUE_LENGTH);
    this.symbol = requireCode("symbol", symbol, MAX_SYMBOL_LENGTH);
    this.symbolBytes = symbol.getBytes(StandardCharsets.US_ASCII);
    this.id = Instruments.UNKNOWN;
  }

  private Instrument(Instrument instrument, int id) {
    this.venue = instrument.venue;
    this.symbol = instrument.symbol;
    this.symbolBytes = instrument.symbolBytes;
    this.id = id;
  }

  /**
   * The same instrument, numbered {@code id} by {@link Instruments}.
   *
   * @param id its index in the shard
   * @return a copy carrying that id
   */
  Instrument withId(int id) {
    return new Instrument(this, id);
  }

  /**
   * Parses the {@code VENUE:SYMBOL} key used across Argus ({@code BINANCE:BTCUSDT}).
   *
   * @param key the key to parse
   * @return the instrument, outside any shard
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

  public String venue() {
    return venue;
  }

  public String symbol() {
    return symbol;
  }

  /**
   * Its index in the shard's {@link Instruments}, or {@link Instruments#UNKNOWN} for an instrument
   * the shard does not relay. Not part of equality: two instruments with the same venue and symbol
   * are equal whatever their id.
   *
   * @return the index, or {@link Instruments#UNKNOWN}
   */
  public int id() {
    return id;
  }

  /**
   * Whether the first {@code length} bytes of {@code bytes} are this instrument's symbol, compared
   * without creating a {@code String}. Allocation-free.
   *
   * @param bytes the buffer holding a symbol
   * @param length the length of the symbol in {@code bytes}
   * @return {@code true} if it is this instrument's symbol
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

  /**
   * Checks a venue or symbol once, so no message has to.
   *
   * @param name what the value is, for the error message
   * @param value the value to check
   * @param maxLength the length of its canonical field
   * @return {@code value}
   * @throws IllegalArgumentException if it is empty, too long or not uppercase letters and digits
   */
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
