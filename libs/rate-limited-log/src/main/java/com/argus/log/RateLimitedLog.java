package com.argus.log;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

/**
 * Logs a problem that repeats at most once per interval: a transport that is down, a model that
 * keeps throwing, a ring that keeps overflowing. Logging each occurrence would flood the log, so
 * the caller counts every one in its metrics, and each line logged says how many there were so far.
 *
 * <p>Safe from any thread. A call that is not logged allocates nothing (a read of the monotonic
 * clock and of an atomic), so it can sit on a hot path; a call that is logged allocates the line,
 * at most once per interval. The interval is measured with the monotonic clock: it times the log
 * only, never events.
 */
public final class RateLimitedLog {

  private final Logger logger;
  private final long intervalNanos;
  private final String interval;
  private final AtomicLong nextLogNanos;

  /**
   * Logs to {@code logger}, the caller's own, at most once every {@code interval}.
   *
   * @param logger the caller's logger
   * @param interval the shortest time between two lines
   * @throws IllegalArgumentException if {@code interval} is negative
   */
  public RateLimitedLog(Logger logger, Duration interval) {
    if (interval.isNegative()) {
      throw new IllegalArgumentException("interval must not be negative");
    }
    this.logger = logger;
    this.intervalNanos = interval.toNanos();
    this.interval = interval.toMillis() + " ms";
    // The first occurrence is always logged
    this.nextLogNanos = new AtomicLong(System.nanoTime());
  }

  /**
   * Logs a failure as an error, with its exception, unless a line was logged in the last interval.
   *
   * @param what what failed, in a few words
   * @param soFar the caller's count of these failures, this one included
   * @param failure the failure
   * @return whether it was logged
   */
  public boolean error(String what, long soFar, Throwable failure) {
    if (!due()) {
      return false;
    }
    logger.error("{} ({} so far; at most one line every {})", what, soFar, interval, failure);
    return true;
  }

  /**
   * Logs a problem as a warning, unless a line was logged in the last interval.
   *
   * @param what what happened, in a few words
   * @param soFar the caller's count of these problems, this one included
   * @return whether it was logged
   */
  public boolean warn(String what, long soFar) {
    if (!due()) {
      return false;
    }
    logger.warn("{} ({} so far; at most one line every {})", what, soFar, interval);
    return true;
  }

  /**
   * Whether a line may be logged now, and if so starts the next interval. Only one thread wins an
   * interval; the others skip it.
   *
   * @return {@code true} if the caller logs now
   */
  private boolean due() {
    long now = System.nanoTime();
    long next = nextLogNanos.get();
    return now - next >= 0 && nextLogNanos.compareAndSet(next, now + intervalNanos);
  }
}
