package com.argus.feedgateway.application;

import com.argus.feedgateway.application.port.in.IngestMarketData;
import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.BestBidAsk;
import com.argus.feedgateway.domain.Decimal64;
import com.argus.feedgateway.domain.Instruments;
import com.argus.feedgateway.domain.Trade;
import java.nio.ByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.MessageHandler;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;

/**
 * Where market data enters the gateway: the inbound adapters hand each event over from their own
 * threads, and the gateway agent {@linkplain #drain drains} the queue on its own thread into the
 * {@link MarketDataRelay}.
 *
 * <ul>
 *   <li>An event that can be judged on its own is rejected before it is queued: an instrument the
 *       gateway does not relay, or a price or quantity that does not fit {@code Decimal64}. The
 *       queue only carries events the relay may publish.
 *   <li>When the queue is full, the event is dropped and counted: an adapter never waits for the
 *       agent.
 * </ul>
 *
 * <p>Events are copied into a pre-allocated ring buffer in a fixed layout, with the instrument as
 * its index in {@link Instruments}. Queueing is thread-safe and draining is single-threaded;
 * neither allocates.
 */
public final class MarketDataQueue implements IngestMarketData {

  private static final int TRADE = 1;
  private static final int BEST_BID_ASK = 2;

  // Trade layout: the longs first, so they stay aligned
  private static final int TRADE_ID = 0;
  private static final int TRADE_PRICE = TRADE_ID + Long.BYTES;
  private static final int TRADE_QUANTITY = TRADE_PRICE + Long.BYTES;
  private static final int TRADE_EXCHANGE_TIMESTAMP = TRADE_QUANTITY + Long.BYTES;
  private static final int TRADE_RECEIVE_TIMESTAMP = TRADE_EXCHANGE_TIMESTAMP + Long.BYTES;
  private static final int TRADE_INSTRUMENT = TRADE_RECEIVE_TIMESTAMP + Long.BYTES;
  private static final int TRADE_AGGRESSOR_SIDE = TRADE_INSTRUMENT + Integer.BYTES;
  private static final int TRADE_LENGTH = TRADE_AGGRESSOR_SIDE + Byte.BYTES;

  // Top-of-book layout
  private static final int BOOK_UPDATE_ID = 0;
  private static final int BOOK_BID_PRICE = BOOK_UPDATE_ID + Long.BYTES;
  private static final int BOOK_BID_QUANTITY = BOOK_BID_PRICE + Long.BYTES;
  private static final int BOOK_ASK_PRICE = BOOK_BID_QUANTITY + Long.BYTES;
  private static final int BOOK_ASK_QUANTITY = BOOK_ASK_PRICE + Long.BYTES;
  private static final int BOOK_EXCHANGE_TIMESTAMP = BOOK_ASK_QUANTITY + Long.BYTES;
  private static final int BOOK_RECEIVE_TIMESTAMP = BOOK_EXCHANGE_TIMESTAMP + Long.BYTES;
  private static final int BOOK_INSTRUMENT = BOOK_RECEIVE_TIMESTAMP + Long.BYTES;
  private static final int BOOK_LENGTH = BOOK_INSTRUMENT + Integer.BYTES;

  // Values in the queue are canonical already: read back as decimals with the canonical exponent
  private static final int CANONICAL = -Decimal64.SCALE;
  // Written explicitly, so reordering the enum cannot change the layout
  private static final byte BUY = 0;
  private static final byte SELL = 1;

  private final Instruments instruments;
  private final MarketDataRelay relay;
  private final MarketDataCounters counters;
  private final RingBuffer events;
  private final MessageHandler eventHandler = this::onEvent;
  // Filled on the agent's thread only, while draining
  private final Trade trade = new Trade();
  private final BestBidAsk book = new BestBidAsk();

  /**
   * @param capacity bytes of the ring buffer; a power of two
   */
  public MarketDataQueue(
      Instruments instruments, MarketDataRelay relay, MarketDataCounters counters, int capacity) {
    this.instruments = instruments;
    this.relay = relay;
    this.counters = counters;
    this.events =
        new ManyToOneRingBuffer(
            new UnsafeBuffer(
                ByteBuffer.allocateDirect(capacity + RingBufferDescriptor.TRAILER_LENGTH)));
  }

  @Override
  public void onTrade(Trade trade) {
    int instrument = instruments.indexOf(trade.instrument());
    if (instrument == Instruments.UNKNOWN) {
      counters.unknownInstrument();
      return;
    }
    if (!trade.isRepresentable()) {
      counters.unrepresentableValue();
      return;
    }
    int index = events.tryClaim(TRADE, TRADE_LENGTH);
    if (index < 0) {
      counters.droppedEvent();
      return;
    }
    MutableDirectBuffer buffer = events.buffer();
    buffer.putLong(index + TRADE_ID, trade.tradeId());
    buffer.putLong(index + TRADE_PRICE, trade.price());
    buffer.putLong(index + TRADE_QUANTITY, trade.quantity());
    buffer.putLong(index + TRADE_EXCHANGE_TIMESTAMP, trade.exchangeTimestamp());
    buffer.putLong(index + TRADE_RECEIVE_TIMESTAMP, trade.receiveTimestamp());
    buffer.putInt(index + TRADE_INSTRUMENT, instrument);
    buffer.putByte(
        index + TRADE_AGGRESSOR_SIDE, trade.aggressorSide() == AggressorSide.SELL ? SELL : BUY);
    events.commit(index);
  }

  @Override
  public void onBestBidAsk(BestBidAsk bestBidAsk) {
    int instrument = instruments.indexOf(bestBidAsk.instrument());
    if (instrument == Instruments.UNKNOWN) {
      counters.unknownInstrument();
      return;
    }
    if (!bestBidAsk.isRepresentable()) {
      counters.unrepresentableValue();
      return;
    }
    int index = events.tryClaim(BEST_BID_ASK, BOOK_LENGTH);
    if (index < 0) {
      counters.droppedEvent();
      return;
    }
    MutableDirectBuffer buffer = events.buffer();
    buffer.putLong(index + BOOK_UPDATE_ID, bestBidAsk.bookUpdateId());
    buffer.putLong(index + BOOK_BID_PRICE, bestBidAsk.bidPrice());
    buffer.putLong(index + BOOK_BID_QUANTITY, bestBidAsk.bidQuantity());
    buffer.putLong(index + BOOK_ASK_PRICE, bestBidAsk.askPrice());
    buffer.putLong(index + BOOK_ASK_QUANTITY, bestBidAsk.askQuantity());
    buffer.putLong(index + BOOK_EXCHANGE_TIMESTAMP, bestBidAsk.exchangeTimestamp());
    buffer.putLong(index + BOOK_RECEIVE_TIMESTAMP, bestBidAsk.receiveTimestamp());
    buffer.putInt(index + BOOK_INSTRUMENT, instrument);
    events.commit(index);
  }

  /**
   * Hands up to {@code limit} queued events to the relay, in the order they were queued. Called by
   * the gateway agent only.
   *
   * @return the number of events drained, for the agent's idle strategy
   */
  public int drain(int limit) {
    return events.read(eventHandler, limit);
  }

  private void onEvent(int type, MutableDirectBuffer buffer, int index, int length) {
    if (type == TRADE) {
      trade
          .instrument(instruments.get(buffer.getInt(index + TRADE_INSTRUMENT)))
          .tradeId(buffer.getLong(index + TRADE_ID))
          .price(buffer.getLong(index + TRADE_PRICE), CANONICAL)
          .quantity(buffer.getLong(index + TRADE_QUANTITY), CANONICAL)
          .aggressorSide(
              buffer.getByte(index + TRADE_AGGRESSOR_SIDE) == SELL
                  ? AggressorSide.SELL
                  : AggressorSide.BUY)
          .exchangeTimestamp(buffer.getLong(index + TRADE_EXCHANGE_TIMESTAMP))
          .receiveTimestamp(buffer.getLong(index + TRADE_RECEIVE_TIMESTAMP));
      relay.onTrade(trade);
    } else {
      book.instrument(instruments.get(buffer.getInt(index + BOOK_INSTRUMENT)))
          .bookUpdateId(buffer.getLong(index + BOOK_UPDATE_ID))
          .bid(
              buffer.getLong(index + BOOK_BID_PRICE),
              CANONICAL,
              buffer.getLong(index + BOOK_BID_QUANTITY),
              CANONICAL)
          .ask(
              buffer.getLong(index + BOOK_ASK_PRICE),
              CANONICAL,
              buffer.getLong(index + BOOK_ASK_QUANTITY),
              CANONICAL)
          .exchangeTimestamp(buffer.getLong(index + BOOK_EXCHANGE_TIMESTAMP))
          .receiveTimestamp(buffer.getLong(index + BOOK_RECEIVE_TIMESTAMP));
      relay.onBestBidAsk(book);
    }
  }
}
