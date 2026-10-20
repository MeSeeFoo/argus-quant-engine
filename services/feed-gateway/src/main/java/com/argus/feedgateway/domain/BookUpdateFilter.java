package com.argus.feedgateway.domain;

import java.util.Arrays;

/**
 * Drops top-of-book updates that are not newer than the last one seen for the same instrument: the
 * book version on the venue ({@code bookUpdateId}) only moves forward. One {@code long} per
 * instrument, no allocation.
 */
public final class BookUpdateFilter {

  private final long[] lastBookUpdateIds;

  public BookUpdateFilter(int instrumentCount) {
    lastBookUpdateIds = new long[instrumentCount];
    Arrays.fill(lastBookUpdateIds, Long.MIN_VALUE);
  }

  /** Returns whether the update is newer than the last accepted one, and remembers it if so. */
  public boolean accept(int instrument, long bookUpdateId) {
    if (bookUpdateId <= lastBookUpdateIds[instrument]) {
      return false;
    }
    lastBookUpdateIds[instrument] = bookUpdateId;
    return true;
  }
}
