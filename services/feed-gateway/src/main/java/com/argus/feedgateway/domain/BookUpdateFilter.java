package com.argus.feedgateway.domain;

import java.util.Arrays;

/**
 * Drops top-of-book updates that are not newer than the last one seen for the same instrument: the
 * book version on the venue ({@code bookUpdateId}) only moves forward. One {@code long} per
 * instrument, no allocation.
 */
public final class BookUpdateFilter {

  private final long[] lastBookUpdateIds;

  /**
   * Creates a filter that has seen nothing yet.
   *
   * @param instrumentCount the number of instruments of the shard
   */
  public BookUpdateFilter(int instrumentCount) {
    lastBookUpdateIds = new long[instrumentCount];
    Arrays.fill(lastBookUpdateIds, Long.MIN_VALUE);
  }

  /**
   * Returns whether the update is newer than the last accepted one, and remembers it if so.
   *
   * @param instrument the instrument's {@link Instrument#id}
   * @param bookUpdateId the book version on the venue
   * @return {@code true} if the update is newer
   */
  public boolean accept(int instrument, long bookUpdateId) {
    if (bookUpdateId <= lastBookUpdateIds[instrument]) {
      return false;
    }
    lastBookUpdateIds[instrument] = bookUpdateId;
    return true;
  }
}
