package com.argus.modelengine.domain.feed;

/**
 * Finds messages lost on the fast path, from jumps in the publisher's {@code sequence}. It only
 * tells what each message reveals; the caller counts it.
 *
 * <p>A sequence only means something within one publisher session. A new session, such as a gateway
 * that restarted or a passive that took over, starts afresh and is not a gap. It assumes one
 * session at a time: while two publishers overlap, each message looks like a new session, and
 * {@link Deduplicator} is what protects the data then (ADR 0022).
 *
 * <p>Called only on the reader thread. Allocation-free.
 */
public final class SequenceGapDetector {

  /** What a message returns when it starts a publisher session, so nothing is known lost. */
  public static final long NEW_SESSION = -1;

  private boolean started = false;
  private int sessionId = 0;
  private long lastSequence = 0;

  /**
   * Records a trade or top-of-book update. Reader thread. Allocation-free.
   *
   * @param session the publisher session it came from
   * @param sequence its sequence
   * @return the messages it reveals as lost, usually 0, or {@link #NEW_SESSION}
   */
  public long onMessage(int session, long sequence) {
    long result = 0;
    if (startsSession(session)) {
      lastSequence = sequence;
      result = NEW_SESSION;
    } else if (sequence > lastSequence) {
      result = sequence - lastSequence - 1;
      lastSequence = sequence;
    }
    return result;
  }

  /**
   * Records a heartbeat, which carries the last sequence published. Reader thread. Allocation-free.
   *
   * @param session the publisher session it came from
   * @param lastPublished the last sequence published on the stream, 0 before the first message
   * @return the messages it reveals as lost, usually 0, or {@link #NEW_SESSION}
   */
  public long onHeartbeat(int session, long lastPublished) {
    long result = 0;
    if (startsSession(session)) {
      lastSequence = lastPublished;
      result = NEW_SESSION;
    } else if (lastPublished > lastSequence) {
      result = lastPublished - lastSequence;
      lastSequence = lastPublished;
    }
    return result;
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
    return true;
  }
}
