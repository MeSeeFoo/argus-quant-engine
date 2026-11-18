package com.argus.modelengine.domain.feed;

import java.util.Arrays;

/**
 * Drops any event whose id is not newer than the last one seen for its instrument: the safety net
 * for a failover and for instruments moving between shards (ADR 0022). One instance per kind of
 * event, since each kind has its own ids: trades by {@code tradeId}, top-of-book updates by {@code
 * bookUpdateId}. Both grow per instrument on the venue. It only decides; the caller counts what it
 * drops.
 *
 * <p>Called only on the reader thread. Allocation-free.
 */
public final class Deduplicator {

  // The newest id accepted per instrument, indexed by instrument id
  private final long[] lastEventsId;

  /**
   * Creates a deduplicator that has seen no events.
   *
   * @param instruments the number of instruments, whose ids index its state
   */
  public Deduplicator(int instruments) {
    lastEventsId = new long[instruments];
    Arrays.fill(lastEventsId, Long.MIN_VALUE);
  }

  /**
   * Decides whether an event is new, and remembers its id if so. Reader thread. Allocation-free.
   *
   * @param instrumentId the event's instrument
   * @param eventId the venue's id of the event: a {@code tradeId} or a {@code bookUpdateId}
   * @return {@code true} if it is newer than the last one of its instrument
   */
  public boolean accept(int instrumentId, long eventId) {
    boolean isNew = eventId > lastEventsId[instrumentId];
    if (isNew) {
      lastEventsId[instrumentId] = eventId;
    }
    return isNew;
  }
}
