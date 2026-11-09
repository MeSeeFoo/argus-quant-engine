package com.argus.feedgateway.utils;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

/**
 * Logs a failure at most once per interval, for adapters that retry in a loop: while their
 * transport is down every attempt fails, and logging each one would flood the log. The adapter
 * counts every failure in its metrics, and each line logged says how many there were so far.
 *
 * <p>Safe from any thread. It measures the interval with the monotonic clock: it times the log
 * only, never events.
 */
public final class FailureLog {

  private final Logger logger;
  private final long intervalNanos;
  private final String interval;
  private final AtomicLong nextLogNanos;

  /**
   * Logs to {@code logger}, the adapter's own, at most once every {@code interval}.
   *
   * @param logger the adapter's logger
   * @param interval the shortest time between two lines
   * @throws IllegalArgumentException if {@code interval} is negative
   */
  public FailureLog(Logger logger, Duration interval) {
    if (interval.isNegative()) {
      throw new IllegalArgumentException("interval must not be negative");
    }
    this.logger = logger;
    this.intervalNanos = interval.toNanos();
    this.interval = interval.toMillis() + " ms";
    // The first failure is always logged
    this.nextLogNanos = new AtomicLong(System.nanoTime());
  }

  /**
   * Logs {@code failure} as an error, unless another one was logged in the last interval.
   *
   * @param what what failed, in a few words
   * @param failedSoFar the adapter's count of these failures, this one included
   * @param failure the failure
   * @return whether it was logged
   */
  public boolean log(String what, long failedSoFar, Exception failure) {
    long now = System.nanoTime();
    long next = nextLogNanos.get();
    // Only one thread wins the interval; the others skip it
    if (now - next < 0 || !nextLogNanos.compareAndSet(next, now + intervalNanos)) {
      return false;
    }
    logger.error(
        "{} ({} failed so far; at most one is logged every {})",
        what,
        failedSoFar,
        interval,
        failure);
    return true;
  }
}
