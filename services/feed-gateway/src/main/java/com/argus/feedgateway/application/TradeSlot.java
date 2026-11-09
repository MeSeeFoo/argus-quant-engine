package com.argus.feedgateway.application;

import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.Decimal64;
import com.argus.feedgateway.domain.Instruments;
import com.argus.feedgateway.domain.Trade;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * The fixed layout of a {@link Trade} in a ring buffer slot: copies its fields in and out, without
 * allocating. The instrument is stored as its index in {@link Instruments}.
 */
final class TradeSlot {

  // The longs first, so they stay aligned
  private static final int TRADE_ID = 0;
  private static final int PRICE = TRADE_ID + Long.BYTES;
  private static final int QUANTITY = PRICE + Long.BYTES;
  private static final int EXCHANGE_TIMESTAMP = QUANTITY + Long.BYTES;
  private static final int RECEIVE_TIMESTAMP = EXCHANGE_TIMESTAMP + Long.BYTES;
  private static final int INSTRUMENT = RECEIVE_TIMESTAMP + Long.BYTES;
  private static final int AGGRESSOR_SIDE = INSTRUMENT + Integer.BYTES;

  /** Bytes of a slot. */
  static final int LENGTH = AGGRESSOR_SIDE + Byte.BYTES;

  // Values in a slot are canonical already: read back as decimals with the canonical exponent
  private static final int CANONICAL = -Decimal64.SCALE;
  // Written explicitly, so reordering the enum cannot change the layout
  private static final byte BUY = 0;
  private static final byte SELL = 1;

  private TradeSlot() {}

  /**
   * Writes {@code trade}, whose instrument has index {@code instrument}, at {@code index}. On an
   * inbound adapter's thread; allocation-free.
   *
   * @param buffer the ring buffer
   * @param index where the claimed slot starts
   * @param trade the values to copy; only read during this call
   * @param instrument the instrument's index in {@link Instruments}
   */
  static void write(MutableDirectBuffer buffer, int index, Trade trade, int instrument) {
    buffer.putLong(index + TRADE_ID, trade.tradeId());
    buffer.putLong(index + PRICE, trade.price());
    buffer.putLong(index + QUANTITY, trade.quantity());
    buffer.putLong(index + EXCHANGE_TIMESTAMP, trade.exchangeTimestamp());
    buffer.putLong(index + RECEIVE_TIMESTAMP, trade.receiveTimestamp());
    buffer.putInt(index + INSTRUMENT, instrument);
    buffer.putByte(
        index + AGGRESSOR_SIDE, trade.aggressorSide() == AggressorSide.SELL ? SELL : BUY);
  }

  /**
   * Fills {@code trade} with the slot at {@code index}. On the gateway agent's thread;
   * allocation-free.
   *
   * @param buffer the ring buffer
   * @param index where the slot starts
   * @param instruments the shard's instruments, to read the instrument back
   * @param trade the reused instance to fill
   */
  static void read(DirectBuffer buffer, int index, Instruments instruments, Trade trade) {
    trade
        .instrument(instruments.get(buffer.getInt(index + INSTRUMENT)))
        .tradeId(buffer.getLong(index + TRADE_ID))
        .price(buffer.getLong(index + PRICE), CANONICAL)
        .quantity(buffer.getLong(index + QUANTITY), CANONICAL)
        .aggressorSide(
            buffer.getByte(index + AGGRESSOR_SIDE) == SELL ? AggressorSide.SELL : AggressorSide.BUY)
        .exchangeTimestamp(buffer.getLong(index + EXCHANGE_TIMESTAMP))
        .receiveTimestamp(buffer.getLong(index + RECEIVE_TIMESTAMP));
  }
}
