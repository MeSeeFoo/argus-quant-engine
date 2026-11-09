package com.argus.modelengine.domain;

/**
 * The stored form of a {@link Trade}: pre-allocated and overwritten when {@link TradeStore} reuses
 * it. Package-private, so only the store can write it and models only see the interface (ADR 0029).
 */
final class MutableTrade implements Trade {

  private int instrumentId;
  private long sequence;
  private long tradeId;
  private long price;
  private long quantity;
  private long exchangeTimestamp;
  private long receiveTimestamp;
  private AggressorSide aggressorSide = AggressorSide.BUY;

  /**
   * Overwrites this object with {@code source}, on the evaluator thread. Allocation-free.
   *
   * @param source the event to copy
   */
  void copyFrom(Trade source) {
    instrumentId = source.instrumentId();
    sequence = source.sequence();
    tradeId = source.tradeId();
    price = source.price();
    quantity = source.quantity();
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
  public long price() {
    return price;
  }

  @Override
  public long quantity() {
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
