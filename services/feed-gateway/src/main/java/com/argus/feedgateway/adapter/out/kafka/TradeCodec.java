package com.argus.feedgateway.adapter.out.kafka;

import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.Decimal64;
import com.argus.feedgateway.domain.Instruments;
import com.argus.feedgateway.domain.Trade;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * The fixed layout of a {@link Trade} in a slot of the Kafka adapter's ring buffer: copies its
 * fields in and out, without allocating. Unlike the gateway's input queue, a slot carries the
 * {@code sequence} the trade was published with, so Kafka gets the same one as Aeron (ADR 0027).
 *
 * <p>The instrument is stored as its {@linkplain com.argus.feedgateway.domain.Instrument#id id},
 * and read back from {@link Instruments}, which already holds its venue and symbol.
 */
final class TradeCodec {

  /** The ring's message type of a trade slot. */
  static final int TYPE = 1;

  // The longs first, so they stay aligned
  private static final int SEQUENCE = 0;
  private static final int TRADE_ID = SEQUENCE + Long.BYTES;
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

  private TradeCodec() {}

  /**
   * Writes {@code trade}, published with {@code sequence}, at {@code index}. Its instrument must
   * come from the shard's {@link Instruments}, as the relay's do. On the gateway agent's thread;
   * allocation-free.
   *
   * @param buffer the ring buffer
   * @param index where the claimed slot starts
   * @param sequence the sequence it was published with
   * @param trade the values to copy; only read during this call
   */
  static void write(MutableDirectBuffer buffer, int index, long sequence, Trade trade) {
    buffer.putLong(index + SEQUENCE, sequence);
    buffer.putLong(index + TRADE_ID, trade.tradeId());
    buffer.putLong(index + PRICE, trade.price());
    buffer.putLong(index + QUANTITY, trade.quantity());
    buffer.putLong(index + EXCHANGE_TIMESTAMP, trade.exchangeTimestamp());
    buffer.putLong(index + RECEIVE_TIMESTAMP, trade.receiveTimestamp());
    buffer.putInt(index + INSTRUMENT, trade.instrument().id());
    buffer.putByte(
        index + AGGRESSOR_SIDE, trade.aggressorSide() == AggressorSide.SELL ? SELL : BUY);
  }

  /**
   * Fills {@code trade} with the slot at {@code index}; its sequence is the slot's first long. On
   * the publisher thread; allocation-free.
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
