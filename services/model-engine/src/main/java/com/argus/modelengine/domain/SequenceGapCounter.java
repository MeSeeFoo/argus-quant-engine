package com.argus.modelengine.domain;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Counts messages lost on the fast path, from jumps in the publisher's {@code sequence}.
 *
 * <p>A sequence only means something within one publisher session. A new session, such as a gateway
 * that restarted or a passive that took over, starts counting afresh and is not a gap. It assumes
 * one session at a time: while two publishers overlap, each message looks like a new session, and
 * {@link TradeDeduplicator} is what protects the data then (ADR 0022).
 *
 * <p>The {@code on...} methods are called only on the reader thread and allocate nothing. The
 * counts may be read from any thread.
 */
public final class SequenceGapCounter {

  private final AtomicLong missedMessages = new AtomicLong();
  private final AtomicLong sessions = new AtomicLong();
  private boolean started;
  private int sessionId;
  private long lastSequence;

  /**
   * Records a trade or top-of-book update. Reader thread. Allocation-free.
   *
   * @param session the publisher session it came from
   * @param sequence its sequence
   */
  public void onMessage(int session, long sequence) {
    if (startsSession(session)) {
      lastSequence = sequence;
    } else if (sequence > lastSequence) {
      missedMessages.addAndGet(sequence - lastSequence - 1);
      lastSequence = sequence;
    }
  }

  /**
   * Records a heartbeat, which carries the last sequence published. Reader thread. Allocation-free.
   *
   * @param session the publisher session it came from
   * @param lastPublished the last sequence published on the stream, 0 before the first message
   */
  public void onHeartbeat(int session, long lastPublished) {
    if (startsSession(session)) {
      lastSequence = lastPublished;
    } else if (lastPublished > lastSequence) {
      missedMessages.addAndGet(lastPublished - lastSequence);
      lastSequence = lastPublished;
    }
  }

  /**
   * Switches to {@code session} if it is not the current one.
   *
   * @param session the session of the current message
   * @return {@code true} if this message starts a session
   */
  private boolean startsSession(int session) {
    if (started && session == sessionId) {
      return false;
    }
    started = true;
    sessionId = session;
    sessions.incrementAndGet();
    return true;
  }

  /**
   * Messages lost within a session. Any thread.
   *
   * @return the count since the engine started
   */
  public long missedMessages() {
    return missedMessages.get();
  }

  /**
   * Publisher sessions seen. Any thread.
   *
   * @return the count since the engine started
   */
  public long sessions() {
    return sessions.get();
  }
}
