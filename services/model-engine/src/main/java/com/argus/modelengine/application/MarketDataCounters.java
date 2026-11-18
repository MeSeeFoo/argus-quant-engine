package com.argus.modelengine.application;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Everything the engine's application counts, what the metrics read: market data lost before it
 * arrived or dropped by the reader's checks, events the ring lost, and evaluations that failed.
 * Written by the reader, through {@link MarketDataEventChecker} and {@link MarketDataService}, and
 * by the {@link Evaluator}; read from any thread. What an adapter rejects before handing it over is
 * counted by that adapter.
 *
 * <p>The {@code count} methods allocate nothing and return the new total, which a rate-limited log
 * can report.
 */
public final class MarketDataCounters {

  private final AtomicLong publisherSessions = new AtomicLong();
  private final AtomicLong missedMessages = new AtomicLong();
  private final AtomicLong duplicateTrades = new AtomicLong();
  private final AtomicLong duplicateBestBidAsks = new AtomicLong();
  private final AtomicLong droppedEvents = new AtomicLong();
  private final AtomicLong skippedEvents = new AtomicLong();
  private final AtomicLong failedEvaluations = new AtomicLong();

  /**
   * Publisher sessions seen: one per gateway that started sending, or took over.
   *
   * @return the count since the engine started
   */
  public long publisherSessions() {
    return publisherSessions.get();
  }

  /**
   * Messages lost within a publisher session, found from jumps in {@code sequence}.
   *
   * @return the count since the engine started
   */
  public long missedMessages() {
    return missedMessages.get();
  }

  /**
   * Trades dropped because they were not newer than the last one of their instrument.
   *
   * @return the count since the engine started
   */
  public long duplicateTrades() {
    return duplicateTrades.get();
  }

  /**
   * Top-of-book updates dropped because they were not newer than the last one of their instrument.
   *
   * @return the count since the engine started
   */
  public long duplicateBestBidAsks() {
    return duplicateBestBidAsks.get();
  }

  /**
   * Events the reader could not publish because the ring was full.
   *
   * @return the count since the engine started
   */
  public long droppedEvents() {
    return droppedEvents.get();
  }

  /**
   * Events the evaluator skipped without evaluating them, to throw away a backlog.
   *
   * @return the count since the engine started
   */
  public long skippedEvents() {
    return skippedEvents.get();
  }

  /**
   * Evaluations that threw, of every model.
   *
   * @return the count since the engine started
   */
  public long failedEvaluations() {
    return failedEvaluations.get();
  }

  /**
   * Counts a publisher session that started. Reader thread.
   *
   * @return the count, this one included
   */
  long countPublisherSession() {
    return publisherSessions.incrementAndGet();
  }

  /**
   * Counts messages found lost. Reader thread.
   *
   * @param messages how many
   * @return the count, these included
   */
  long countMissedMessages(long messages) {
    return missedMessages.addAndGet(messages);
  }

  /**
   * Counts a duplicate or old trade. Reader thread.
   *
   * @return the count, this one included
   */
  long countDuplicateTrade() {
    return duplicateTrades.incrementAndGet();
  }

  /**
   * Counts a duplicate or old top-of-book update. Reader thread.
   *
   * @return the count, this one included
   */
  long countDuplicateBestBidAsk() {
    return duplicateBestBidAsks.incrementAndGet();
  }

  /**
   * Counts an event dropped with the ring full. Reader thread.
   *
   * @return the count, this one included
   */
  long countDroppedEvent() {
    return droppedEvents.incrementAndGet();
  }

  /**
   * Counts events skipped to throw away a backlog. Evaluator thread.
   *
   * @param events how many were skipped
   * @return the count, these included
   */
  long countSkippedEvents(long events) {
    return skippedEvents.addAndGet(events);
  }

  /**
   * Counts an evaluation that threw. Evaluator thread.
   *
   * @return the count, this one included
   */
  long countFailedEvaluation() {
    return failedEvaluations.incrementAndGet();
  }
}
