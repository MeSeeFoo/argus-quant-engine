package com.argus.modelengine.application;

import com.argus.log.RateLimitedLog;
import com.lmax.disruptor.Cursored;
import com.lmax.disruptor.Sequence;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies the {@link BackpressureStrategy} when the ring is full, and counts what it loses in the
 * {@link MarketDataCounters}. Shared by the reader, which reports a full ring, and the evaluator,
 * which skips the backlog when the strategy is {@link BackpressureStrategy#DROP_OLDEST}.
 * Allocation-free.
 *
 * <p>Losses are logged by the evaluator, at most once per interval, whatever the strategy: the
 * reader only counts ring losses, so it never logs them.
 */
public final class RingOverflowMechanism {

  private static final Logger LOG = LoggerFactory.getLogger(RingOverflowMechanism.class);
  // While the evaluator is behind every event may be lost: the counters count them, the log samples
  private static final Duration LOSS_LOG_INTERVAL = Duration.ofSeconds(10);

  private final BackpressureStrategy strategy;
  private final Cursored ring;
  private final MarketDataCounters counters;
  // Raised by the reader, cleared by the evaluator when it skips
  private final AtomicBoolean skipRequested = new AtomicBoolean();
  private final RateLimitedLog lossLog = new RateLimitedLog(LOG, LOSS_LOG_INTERVAL);
  // Built once, so logging a loss builds no message
  private final String lossMessage;
  // Evaluator thread only: the losses already logged
  private long logged = 0;

  /**
   * Creates the overflow handling of a ring. Startup only.
   *
   * @param strategy what to drop when the ring is full
   * @param ring the ring, whose cursor is its last published sequence
   * @param counters where dropped and skipped events are counted
   */
  RingOverflowMechanism(BackpressureStrategy strategy, Cursored ring, MarketDataCounters counters) {
    this.strategy = strategy;
    this.ring = ring;
    this.counters = counters;
    this.lossMessage =
        "The ring between the reader and the evaluator is full, so events are lost with "
            + strategy
            + ": the evaluator is behind";
  }

  /**
   * The strategy applied when the ring is full.
   *
   * @return the strategy
   */
  public BackpressureStrategy strategy() {
    return strategy;
  }

  /**
   * Reports that the ring is full: the event that did not fit is dropped and counted, and with
   * {@link BackpressureStrategy#DROP_OLDEST} the evaluator is asked to skip its backlog. Reader
   * thread.
   */
  void onFull() {
    counters.countDroppedEvent();
    if (strategy == BackpressureStrategy.DROP_OLDEST) {
      skipRequested.set(true);
    }
  }

  /**
   * Skips everything published so far if the reader asked for it, by moving the evaluator's
   * sequence to the ring's cursor, and counts what was skipped. Evaluator thread, between two
   * polls: the evaluator is not reading any slot then, so moving its sequence is safe. The flag is
   * cleared before the cursor is read, so a ring that fills again right after raises it again.
   *
   * @param consumed the evaluator's sequence, the last slot it has handled
   */
  void skipIfRequested(Sequence consumed) {
    if (skipRequested.get()) {
      skipRequested.set(false);
      long cursor = ring.getCursor();
      counters.countSkippedEvents(cursor - consumed.get());
      consumed.set(cursor);
    }
  }

  /**
   * Logs the events lost since the last line, dropped by the reader or skipped by the evaluator, at
   * most once per interval. Evaluator thread, once per duty cycle. Allocation-free while nothing is
   * lost, or while the interval has not passed.
   */
  void logLosses() {
    long lost = counters.droppedEvents() + counters.skippedEvents();
    if (lost != logged && lossLog.warn(lossMessage, lost)) {
      logged = lost;
    }
  }
}
