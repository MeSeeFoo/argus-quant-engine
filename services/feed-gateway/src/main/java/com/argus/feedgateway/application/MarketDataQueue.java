package com.argus.feedgateway.application;

import com.argus.feedgateway.application.port.in.IngestMarketData;
import com.argus.feedgateway.domain.BestBidAsk;
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
 * <p>Events are copied into a pre-allocated ring buffer in a fixed layout ({@link TradeSlot},
 * {@link BidAskSlot}), with the instrument as its {@linkplain
 * com.argus.feedgateway.domain.Instrument#id id}. Queueing is thread-safe and draining is
 * single-threaded; neither allocates.
 *
 * <p>Why bytes and not a ring of reusable {@code Trade} objects, such as the LMAX Disruptor's:
 *
 * <ul>
 *   <li>The hard part is not creating objects but handing them between threads: several producers
 *       (the WebSocket threads) and one consumer (the agent) need to claim a slot, write it,
 *       publish it with a memory barrier and free it. Agrona's ring buffer already does this, and
 *       Agrona is on the classpath for Aeron; the Disruptor would be a second ring buffer library.
 *   <li>The Disruptor's non-blocking claim with several producers, {@code tryNext()}, reports a
 *       full ring by throwing an exception. The hot path does not use exceptions as control flow,
 *       and the alternative, {@code next()}, waits for a free slot, which an adapter must never do.
 *   <li>The copy costs the same either way: a handful of {@code long} fields, no encoding.
 * </ul>
 */
public final class MarketDataQueue implements IngestMarketData {

  private static final int TRADE = 1;
  private static final int BEST_BID_ASK = 2;

  private final Instruments instruments;
  private final MarketDataRelay relay;
  private final MarketDataCounters counters;
  private final RingBuffer events;
  private final MessageHandler eventHandler = this::onEvent;
  // Filled on the agent's thread only, while draining
  private final Trade trade = new Trade();
  private final BestBidAsk book = new BestBidAsk();

  /**
   * Creates the queue and allocates its ring buffer once.
   *
   * @param instruments the shard's instruments
   * @param relay where drained events go
   * @param counters the gateway's counters
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
    int instrument = trade.instrument().id();
    if (instrument == Instruments.UNKNOWN) {
      counters.unknownInstrument();
      return;
    }
    if (!trade.isRepresentable()) {
      counters.unrepresentableValue();
      return;
    }
    int index = events.tryClaim(TRADE, TradeSlot.LENGTH);
    if (index < 0) {
      counters.droppedEvent();
      return;
    }
    TradeSlot.write(events.buffer(), index, trade, instrument);
    events.commit(index);
  }

  @Override
  public void onBestBidAsk(BestBidAsk bestBidAsk) {
    int instrument = bestBidAsk.instrument().id();
    if (instrument == Instruments.UNKNOWN) {
      counters.unknownInstrument();
      return;
    }
    if (!bestBidAsk.isRepresentable()) {
      counters.unrepresentableValue();
      return;
    }
    int index = events.tryClaim(BEST_BID_ASK, BidAskSlot.LENGTH);
    if (index < 0) {
      counters.droppedEvent();
      return;
    }
    BidAskSlot.write(events.buffer(), index, bestBidAsk, instrument);
    events.commit(index);
  }

  /**
   * Hands up to {@code limit} queued events to the relay, in the order they were queued. Called by
   * the gateway agent only.
   *
   * @param limit the most events to drain in this call
   * @return the number of events drained, for the agent's idle strategy
   */
  public int drain(int limit) {
    return events.read(eventHandler, limit);
  }

  /**
   * Hands one slot of the ring to the relay, on the agent's thread. Allocation-free.
   *
   * @param type the slot's type, {@link #TRADE} or {@link #BEST_BID_ASK}
   * @param buffer the ring buffer
   * @param index where the slot starts
   * @param length the length of the slot in bytes
   */
  private void onEvent(int type, MutableDirectBuffer buffer, int index, int length) {
    if (type == TRADE) {
      TradeSlot.read(buffer, index, instruments, trade);
      relay.sendTrade(trade);
    } else {
      BidAskSlot.read(buffer, index, instruments, book);
      relay.sendBestBidAsk(book);
    }
  }
}
