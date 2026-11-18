package com.argus.modelengine.adapter.in.aeron.decoder;

import com.argus.contracts.sbe.Side;
import com.argus.contracts.sbe.TradeDecoder;
import com.argus.modelengine.domain.marketdata.AggressorSide;
import com.argus.modelengine.domain.marketdata.Trade;
import org.jspecify.annotations.Nullable;

/**
 * A read-only view of the trade a decoder is wrapped around. It reads straight from Aeron's buffer,
 * so it is valid only while the decoder is wrapped around that message.
 */
public final class DecodedTrade implements Trade {

  private final TradeDecoder decoder;
  private int instrumentId = 0;
  private AggressorSide side = AggressorSide.BUY;

  /**
   * Creates a view over a decoder.
   *
   * @param decoder the decoder it reads from
   */
  public DecodedTrade(TradeDecoder decoder) {
    this.decoder = decoder;
  }

  /**
   * Maps the wire value of a trade's side. The raw value, because decoding an enum throws on a
   * value it does not know.
   *
   * @param raw the value on the wire
   * @return its side, or {@code null} if it is not a valid one
   */
  public static @Nullable AggressorSide aggressorSide(short raw) {
    if (raw == Side.BUY.value()) {
      return AggressorSide.BUY;
    }
    if (raw == Side.SELL.value()) {
      return AggressorSide.SELL;
    }
    return null;
  }

  /**
   * Sets what the decoder does not give directly.
   *
   * @param instrumentId the engine's id of the instrument
   * @param side the trade's side
   */
  public void set(int instrumentId, AggressorSide side) {
    this.instrumentId = instrumentId;
    this.side = side;
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
  public long tradeId() {
    return decoder.tradeId();
  }

  @Override
  public long priceMantissa() {
    return decoder.price().mantissa();
  }

  @Override
  public long quantityMantissa() {
    return decoder.quantity().mantissa();
  }

  @Override
  public AggressorSide aggressorSide() {
    return side;
  }
}
