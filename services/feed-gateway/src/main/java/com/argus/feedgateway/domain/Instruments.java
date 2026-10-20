package com.argus.feedgateway.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The instruments of the gateway's shard, numbered from 0 in configuration order. Per-instrument
 * state is kept in arrays indexed by that number, so the hot path never looks anything up in a map.
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
    this.instruments = instruments.toArray(Instrument[]::new);
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
}
