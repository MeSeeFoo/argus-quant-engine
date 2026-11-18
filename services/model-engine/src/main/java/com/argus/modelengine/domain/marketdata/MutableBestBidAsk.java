package com.argus.modelengine.domain.marketdata;

/**
 * A reusable {@link BestBidAsk}: pre-allocated, and overwritten with {@link #copyFrom} each time it
 * is reused. Windows keep their copies of events in these, and each slot of the Disruptor ring
 * holds one (ADR 0029). Models only ever receive it as the read-only {@link BestBidAsk}.
 *
 * <p>Not thread-safe: one thread writes it at a time, and the Disruptor's sequences publish the
 * writes to the thread that reads it next. Allocation-free.
 */
public final class MutableBestBidAsk implements BestBidAsk {

  private int instrumentId;
  private long sequence;
  private long bookUpdateId;
  private long bidPrice;
  private long bidQuantity;
  private long askPrice;
  private long askQuantity;
  private long exchangeTimestamp;
  private long receiveTimestamp;

  /**
   * Overwrites this object with {@code source}: on the reader thread in a ring slot, on the
   * evaluator thread in a window. Allocation-free.
   *
   * @param source the event to copy
   */
  public void copyFrom(BestBidAsk source) {
    instrumentId = source.instrumentId();
    sequence = source.sequence();
    bookUpdateId = source.bookUpdateId();
    bidPrice = source.bidPriceMantissa();
    bidQuantity = source.bidQuantityMantissa();
    askPrice = source.askPriceMantissa();
    askQuantity = source.askQuantityMantissa();
    exchangeTimestamp = source.exchangeTimestamp();
    receiveTimestamp = source.receiveTimestamp();
  }

  @Override
  public int instrumentId() {
    return instrumentId;
  }

  @Override
  public long sequence() {
    return sequence;
  }

  @Override
  public long bookUpdateId() {
    return bookUpdateId;
  }

  @Override
  public long bidPriceMantissa() {
    return bidPrice;
  }

  @Override
  public long bidQuantityMantissa() {
    return bidQuantity;
  }

  @Override
  public long askPriceMantissa() {
    return askPrice;
  }

  @Override
  public long askQuantityMantissa() {
    return askQuantity;
  }

  @Override
  public long exchangeTimestamp() {
    return exchangeTimestamp;
  }

  @Override
  public long receiveTimestamp() {
    return receiveTimestamp;
  }
}
