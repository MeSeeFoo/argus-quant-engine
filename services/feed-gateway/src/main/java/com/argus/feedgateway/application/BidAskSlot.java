package com.argus.feedgateway.application;

import com.argus.feedgateway.domain.BestBidAsk;
import com.argus.feedgateway.domain.Decimal64;
import com.argus.feedgateway.domain.Instruments;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * The fixed layout of a {@link BestBidAsk} in a ring buffer slot: copies its fields in and out,
 * without allocating. The instrument is stored as its index in {@link Instruments}.
 */
final class BidAskSlot {

  private static final int BOOK_UPDATE_ID = 0;
  private static final int BID_PRICE = BOOK_UPDATE_ID + Long.BYTES;
  private static final int BID_QUANTITY = BID_PRICE + Long.BYTES;
  private static final int ASK_PRICE = BID_QUANTITY + Long.BYTES;
  private static final int ASK_QUANTITY = ASK_PRICE + Long.BYTES;
  private static final int EXCHANGE_TIMESTAMP = ASK_QUANTITY + Long.BYTES;
  private static final int RECEIVE_TIMESTAMP = EXCHANGE_TIMESTAMP + Long.BYTES;
  private static final int INSTRUMENT = RECEIVE_TIMESTAMP + Long.BYTES;

  /** Bytes of a slot. */
  static final int LENGTH = INSTRUMENT + Integer.BYTES;

  // Values in a slot are canonical already: read back as decimals with the canonical exponent
  private static final int CANONICAL = -Decimal64.SCALE;

  private BidAskSlot() {}

  /**
   * Writes {@code book}, whose instrument has index {@code instrument}, at {@code index}. On an
   * inbound adapter's thread; allocation-free.
   *
   * @param buffer the ring buffer
   * @param index where the claimed slot starts
   * @param book the values to copy; only read during this call
   * @param instrument the instrument's index in {@link Instruments}
   */
  static void write(MutableDirectBuffer buffer, int index, BestBidAsk book, int instrument) {
    buffer.putLong(index + BOOK_UPDATE_ID, book.bookUpdateId());
    buffer.putLong(index + BID_PRICE, book.bidPrice());
    buffer.putLong(index + BID_QUANTITY, book.bidQuantity());
    buffer.putLong(index + ASK_PRICE, book.askPrice());
    buffer.putLong(index + ASK_QUANTITY, book.askQuantity());
    buffer.putLong(index + EXCHANGE_TIMESTAMP, book.exchangeTimestamp());
    buffer.putLong(index + RECEIVE_TIMESTAMP, book.receiveTimestamp());
    buffer.putInt(index + INSTRUMENT, instrument);
  }

  /**
   * Fills {@code book} with the slot at {@code index}. On the gateway agent's thread;
   * allocation-free.
   *
   * @param buffer the ring buffer
   * @param index where the slot starts
   * @param instruments the shard's instruments, to read the instrument back
   * @param book the reused instance to fill
   */
  static void read(DirectBuffer buffer, int index, Instruments instruments, BestBidAsk book) {
    book.instrument(instruments.get(buffer.getInt(index + INSTRUMENT)))
        .bookUpdateId(buffer.getLong(index + BOOK_UPDATE_ID))
        .bid(
            buffer.getLong(index + BID_PRICE),
            CANONICAL,
            buffer.getLong(index + BID_QUANTITY),
            CANONICAL)
        .ask(
            buffer.getLong(index + ASK_PRICE),
            CANONICAL,
            buffer.getLong(index + ASK_QUANTITY),
            CANONICAL)
        .exchangeTimestamp(buffer.getLong(index + EXCHANGE_TIMESTAMP))
        .receiveTimestamp(buffer.getLong(index + RECEIVE_TIMESTAMP));
  }
}
