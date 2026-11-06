package com.argus.feedgateway.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The instruments of the gateway's shard, numbered from 0 in configuration order. Each one carries
 * its number as its {@link Instrument#id}, and per-instrument state is kept in arrays indexed by
 * it, so the hot path never looks anything up. Events must carry the instruments returned by {@link
 * #get}: an equal instrument created elsewhere has no id.
 */
public final class Instruments {

  /** Returned by {@link #indexOf} for a symbol that is not in the shard. */
  public static final int UNKNOWN = -1;

  private final Instrument[] instruments;

  public Instruments(List<Instrument> instruments) {
    if (instruments.isEmpty()) {
      throw new IllegalArgumentException("at least one instrument is required");
    }
    Set<String> symbols = new HashSet<>();
    for (Instrument instrument : instruments) {
      if (!symbols.add(instrument.symbol())) {
        throw new IllegalArgumentException("duplicate symbol " + instrument);
      }
    }
    this.instruments = new Instrument[instruments.size()];
    for (int i = 0; i < this.instruments.length; i++) {
      this.instruments[i] = instruments.get(i).withId(i);
    }
  }

  public int size() {
    return instruments.length;
  }

  public Instrument get(int index) {
    return instruments[index];
  }

  /**
   * Returns the index of the instrument whose symbol is the first {@code length} bytes of {@code
   * symbol}, or {@link #UNKNOWN}. A shard holds a handful of instruments, so a linear scan is
   * enough.
   */
  public int indexOf(byte[] symbol, int length) {
    for (int i = 0; i < instruments.length; i++) {
      if (instruments[i].hasSymbol(symbol, length)) {
        return i;
      }
    }
    return UNKNOWN;
  }

  /**
   * Returns the index of an instrument equal to {@code instrument}, or {@link #UNKNOWN}. A linear
   * scan, for configuration: the hot path uses {@link Instrument#id}.
   */
  public int indexOf(Instrument instrument) {
    for (int i = 0; i < instruments.length; i++) {
      if (instruments[i].equals(instrument)) {
        return i;
      }
    }
    return UNKNOWN;
  }
}
