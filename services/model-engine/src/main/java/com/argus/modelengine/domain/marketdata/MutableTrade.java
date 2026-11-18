package com.argus.modelengine.domain.marketdata;

/**
 * A reusable {@link Trade}: pre-allocated, and overwritten with {@link #copyFrom} each time it is
 * reused. Windows keep their copies of events in these, and each slot of the Disruptor ring holds
 * one (ADR 0029). Models only ever receive it as the read-only {@link Trade}.
 *
 * <p>Not thread-safe: one thread writes it at a time, and the Disruptor's sequences publish the
 * writes to the thread that reads it next. Allocation-free.
 */
public final class MutableTrade implements Trade {

  private int instrumentId;
  private long sequence;
  private long tradeId;
  private long price;
  private long quantity;
  private long exchangeTimestamp;
  private long receiveTimestamp;
  private AggressorSide aggressorSide = AggressorSide.BUY;

  /**
   * Overwrites this object with {@code source}: on the reader thread in a ring slot, on the
   * evaluator thread in a window. Allocation-free.
   *
   * @param source the event to copy
   */
  public void copyFrom(Trade source) {
    instrumentId = source.instrumentId();
    sequence = source.sequence();
    tradeId = source.tradeId();
    price = source.priceMantissa();
    quantity = source.quantityMantissa();
    exchangeTimestamp = source.exchangeTimestamp();
    receiveTimestamp = source.receiveTimestamp();
    aggressorSide = source.aggressorSide();
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
  public long tradeId() {
    return tradeId;
  }

  @Override
  public long priceMantissa() {
    return price;
  }

  @Override
  public long quantityMantissa() {
    return quantity;
  }

  @Override
  public long exchangeTimestamp() {
    return exchangeTimestamp;
  }

  @Override
  public long receiveTimestamp() {
    return receiveTimestamp;
  }

  @Override
  public AggressorSide aggressorSide() {
    return aggressorSide;
  }
}
