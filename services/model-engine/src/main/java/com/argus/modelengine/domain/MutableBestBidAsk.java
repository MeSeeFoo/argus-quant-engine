package com.argus.modelengine.domain;

/**
 * The stored form of a {@link BestBidAsk}: pre-allocated and overwritten when {@link
 * BestBidAskStore} reuses it. Package-private, so only the store can write it and models only see
 * the interface (ADR 0029).
 */
final class MutableBestBidAsk implements BestBidAsk {

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
   * Overwrites this object with {@code source}, on the evaluator thread. Allocation-free.
   *
   * @param source the event to copy
   */
  void copyFrom(BestBidAsk source) {
    instrumentId = source.instrumentId();
    sequence = source.sequence();
    bookUpdateId = source.bookUpdateId();
    bidPrice = source.bidPrice();
    bidQuantity = source.bidQuantity();
    askPrice = source.askPrice();
    askQuantity = source.askQuantity();
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
  public long bidPrice() {
    return bidPrice;
  }

  @Override
  public long bidQuantity() {
    return bidQuantity;
  }

  @Override
  public long askPrice() {
    return askPrice;
  }

  @Override
  public long askQuantity() {
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
