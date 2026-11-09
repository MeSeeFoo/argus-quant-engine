package com.argus.modelengine.domain;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The instruments the engine handles, numbered from 0 in configuration order, until the instrument
 * catalogue arrives (ADR 0023). The number is the id that indexes per-instrument state, so the hot
 * path only looks an instrument up once per message.
 *
 * <p>Immutable after construction, so safe from any thread.
 */
public final class Instruments {

  /** Returned by {@link #indexOf} for an instrument the engine does not handle. */
  public static final int UNKNOWN = -1;

  private final Instrument[] instruments;
  private final byte[][] venues;
  private final byte[][] symbols;

  /**
   * Numbers the instruments from 0, in the given order.
   *
   * @param instruments the instruments to handle
   * @throws IllegalArgumentException if the list is empty or repeats an instrument
   */
  public Instruments(List<Instrument> instruments) {
    if (instruments.isEmpty()) {
      throw new IllegalArgumentException("at least one instrument is required");
    }
    Set<Instrument> seen = new HashSet<>();
    for (Instrument instrument : instruments) {
      if (!seen.add(instrument)) {
        throw new IllegalArgumentException("duplicate instrument " + instrument);
      }
    }
    this.instruments = instruments.toArray(new Instrument[0]);
    this.venues = new byte[this.instruments.length][];
    this.symbols = new byte[this.instruments.length][];
    for (int i = 0; i < this.instruments.length; i++) {
      this.venues[i] = canonical(this.instruments[i].venue(), Instrument.VENUE_LENGTH);
      this.symbols[i] = canonical(this.instruments[i].symbol(), Instrument.SYMBOL_LENGTH);
    }
  }

  /**
   * The number of instruments: per-instrument arrays have this length.
   *
   * @return the number of instruments
   */
  public int size() {
    return instruments.length;
  }

  /**
   * The instrument numbered {@code id}.
   *
   * @param id from 0 to {@link #size} (exclusive)
   * @return the instrument
   */
  public Instrument get(int id) {
    return instruments[id];
  }

  /**
   * Finds an instrument by its raw canonical fields, uppercase US-ASCII and null-padded as SBE
   * carries them. Any thread. Allocation-free: the engine handles a handful of instruments, so a
   * linear scan is enough.
   *
   * @param venue a buffer holding at least {@link Instrument#VENUE_LENGTH} bytes
   * @param symbol a buffer holding at least {@link Instrument#SYMBOL_LENGTH} bytes
   * @return the instrument's id, or {@link #UNKNOWN}
   */
  public int indexOf(byte[] venue, byte[] symbol) {
    for (int i = 0; i < instruments.length; i++) {
      if (Arrays.equals(
              symbols[i], 0, Instrument.SYMBOL_LENGTH, symbol, 0, Instrument.SYMBOL_LENGTH)
          && Arrays.equals(
              venues[i], 0, Instrument.VENUE_LENGTH, venue, 0, Instrument.VENUE_LENGTH)) {
        return i;
      }
    }
    return UNKNOWN;
  }

  /**
   * Encodes a field as SBE carries it.
   *
   * @param value the venue or symbol
   * @param length the length of its canonical field
   * @return the bytes, null-padded to {@code length}
   */
  private static byte[] canonical(String value, int length) {
    return Arrays.copyOf(value.getBytes(StandardCharsets.US_ASCII), length);
  }
}
