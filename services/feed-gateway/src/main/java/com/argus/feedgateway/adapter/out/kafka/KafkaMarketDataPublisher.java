package com.argus.feedgateway.adapter.out.kafka;

import com.argus.feedgateway.application.port.out.MarketDataPublisher;
import com.argus.feedgateway.domain.BestBidAsk;
import com.argus.feedgateway.domain.Trade;
import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;

/**
 * The durable path's {@link MarketDataPublisher}: the gateway agent's side of the Kafka adapter,
 * which never blocks it (ADR 0005, ADR 0027).
 *
 * <p>Two threads, one ring buffer between them. This class runs on the gateway agent's thread: it
 * only copies each event into the ring ({@link TradeCodec}, {@link BestBidAskCodec}), without
 * allocating, and drops and counts it when the ring is full. {@link KafkaSenderAgent} runs on its
 * own thread: it drains the ring, conflates the top of book and sends to Kafka.
 */
public final class KafkaMarketDataPublisher implements MarketDataPublisher {

  private final RingBuffer events;
  private final KafkaCounters counters;

  /**
   * Creates a publisher with a ring buffer of {@code capacity} bytes.
   *
   * @param capacity the ring's capacity in bytes, a power of two
   * @param counters the adapter's counters
   */
  public KafkaMarketDataPublisher(int capacity, KafkaCounters counters) {
    this.events =
        new OneToOneRingBuffer(
            new UnsafeBuffer(
                ByteBuffer.allocateDirect(capacity + RingBufferDescriptor.TRAILER_LENGTH)));
    this.counters = counters;
  }

  @Override
  public void publishTrade(Trade trade, long sequence) {
    int index = events.tryClaim(TradeCodec.TYPE, TradeCodec.LENGTH);
    if (index < 0) {
      counters.countDroppedRingFull();
      return;
    }
    TradeCodec.write(events.buffer(), index, sequence, trade);
    events.commit(index);
  }

  @Override
  public void publishBestBidAsk(BestBidAsk bestBidAsk, long sequence) {
    int index = events.tryClaim(BestBidAskCodec.TYPE, BestBidAskCodec.LENGTH);
    if (index < 0) {
      counters.countDroppedRingFull();
      return;
    }
    BestBidAskCodec.write(events.buffer(), index, sequence, bestBidAsk);
    events.commit(index);
  }

  /** Does nothing: heartbeats exist on the fast path only (ADR 0027). */
  @Override
  public void publishHeartbeat(long epochNow, long sequence) {}

  /**
   * The ring this publisher writes, for the {@link KafkaSenderAgent} that drains it.
   *
   * @return the ring buffer
   */
  RingBuffer events() {
    return events;
  }
}
