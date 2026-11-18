package com.argus.modelengine.adapter.in.aeron.decoder;

import com.argus.contracts.sbe.BestBidAskDecoder;
import com.argus.modelengine.domain.marketdata.BestBidAsk;

/**
 * A read-only view of the top-of-book update a decoder is wrapped around. It reads straight from
 * Aeron's buffer, so it is valid only while the decoder is wrapped around that message.
 */
public final class DecodedBestBidAsk implements BestBidAsk {

  private final BestBidAskDecoder decoder;
  private int instrumentId = 0;

  /**
   * Creates a view over a decoder.
   *
   * @param decoder the decoder it reads from
   */
  public DecodedBestBidAsk(BestBidAskDecoder decoder) {
    this.decoder = decoder;
  }

  /**
   * Sets what the decoder does not give directly.
   *
   * @param instrumentId the engine's id of the instrument
   */
  public void set(int instrumentId) {
    this.instrumentId = instrumentId;
  }

  @Override
  public int instrumentId() {
    return instrumentId;
  }

  @Override
  public long sequence() {
    return decoder.sequence();
  }

  @Override
  public long exchangeTimestamp() {
    return decoder.exchangeTimestamp();
  }

  @Override
  public long receiveTimestamp() {
    return decoder.receiveTimestamp();
  }

  @Override
  public long bookUpdateId() {
    return decoder.bookUpdateId();
  }

  @Override
  public long bidPriceMantissa() {
    return decoder.bidPrice().mantissa();
  }

  @Override
  public long bidQuantityMantissa() {
    return decoder.bidQuantity().mantissa();
  }

  @Override
  public long askPriceMantissa() {
    return decoder.askPrice().mantissa();
  }

  @Override
  public long askQuantityMantissa() {
    return decoder.askQuantity().mantissa();
  }
}
