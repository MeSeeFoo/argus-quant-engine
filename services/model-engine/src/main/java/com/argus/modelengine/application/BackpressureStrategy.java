package com.argus.modelengine.application;

/**
 * What the engine drops when the Disruptor ring between the reader and the evaluator is full (ADR
 * 0029). The reader never stops taking what Aeron has: if data is there and the reader does not
 * take it, the reader is waiting, even without blocking its thread. So a full ring always loses
 * data, and this says which.
 */
public enum BackpressureStrategy {

  /**
   * The event that does not fit is dropped and counted, and the ring keeps what it holds. Simple
   * and entirely on the reader's side, but while the overload lasts the evaluator works on older
   * data and the fresh data is thrown away.
   */
  DROP_NEWEST,

  /**
   * The evaluator throws away the backlog. The reader cannot overwrite the oldest slots itself:
   * they are the ones the evaluator has not read, and it may be reading one at that moment. So the
   * reader drops the event that does not fit, counts it and raises a flag; at the start of its next
   * duty cycle the evaluator moves its sequence to the ring's cursor, skipping the whole backlog
   * without evaluating it, and counts it. The ring is empty again and fresh data flows at once;
   * only the few newest events that arrive before the evaluator reacts are lost.
   */
  DROP_OLDEST
}
