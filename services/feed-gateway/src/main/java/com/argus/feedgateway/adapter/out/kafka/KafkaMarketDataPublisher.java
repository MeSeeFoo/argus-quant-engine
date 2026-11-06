package com.argus.feedgateway.adapter.out.kafka;

import com.argus.feedgateway.application.port.out.MarketDataPublisher;
import com.argus.feedgateway.domain.BestBidAsk;
import com.argus.feedgateway.domain.Instruments;
import com.argus.feedgateway.domain.Trade;
import com.argus.feedgateway.utils.FailureLog;
import com.google.protobuf.Message;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.stream.Stream;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.MessageHandler;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.ProducerListener;

/**
 * The durable path's {@link MarketDataPublisher}: publishes every trade and the conflated top of
 * book to Kafka as canonical Protobuf messages, without ever blocking the gateway agent (ADR 0005,
 * ADR 0027).
 *
 * <p>Two threads, one ring buffer between them. The gateway agent only copies each event into the
 * ring ({@link KafkaTradeSlot}, {@link KafkaBidAskSlot}), without allocating, and drops and counts
 * it when the ring is full. The publisher thread drains the ring. It maps each trade to Protobuf
 * and hands it to the {@link KafkaTemplate}, which may block or allocate on that thread only.
 *
 * <p>A top of book is not sent when drained: it overwrites its instrument's pre-allocated slot, and
 * every interval the slots that changed are sent. The interval is measured on a monotonic clock, so
 * the last change still goes out when the market stops.
 */
public final class KafkaMarketDataPublisher implements MarketDataPublisher {

  private static final int TRADE = 1;
  private static final int BEST_BID_ASK = 2;
  private static final int EVENT_LIMIT = 64;
  private static final Logger LOG = LoggerFactory.getLogger(KafkaMarketDataPublisher.class);
  // While Kafka is down every record fails: the counters count them, the log samples them
  private static final Duration FAILURE_LOG_INTERVAL = Duration.ofSeconds(10);

  private final KafkaTemplate<String, Message> kafkaTemplate;
  private final KafkaProperties properties;
  private final Instruments instruments;
  private final RingBuffer events;
  private final MessageSenderAgent messageSenderAgent;

  private final KafkaCounters kafkaCounters;
  private final FailureLog failureLog = new FailureLog(LOG, FAILURE_LOG_INTERVAL);
  private final MessageHandler eventHandler = this::onEvent;

  // Filled on the publisher thread only, while draining
  private final Trade reusedTrade = new Trade();
  private final BestBidAsk reusedBestBidAsk = new BestBidAsk();
  private final NanoClock monotonicClock;
  private final BestBidAsk[] bestBidAskConflationCache;
  private final long[] bestBidAskSequenceCache;
  private final boolean[] bestBidAskChanged;
  private final long bestBidAskIntervalNanos;
  private long lastTimeSent;

  /**
   * Sends with {@code kafkaTemplate}, through a ring buffer of {@code properties.bufferCapacity()}
   * bytes, the events of {@code instruments}: the shard's, which the relay's events come from.
   * {@code monotonicClock} measures the interval of the top of book.
   */
  public KafkaMarketDataPublisher(
      KafkaTemplate<String, Message> kafkaTemplate,
      KafkaProperties properties,
      Instruments instruments,
      KafkaCounters kafkaCounters,
      NanoClock monotonicClock) {
    this.kafkaTemplate = kafkaTemplate;
    this.properties = properties;
    this.instruments = instruments;
    this.kafkaCounters = kafkaCounters;
    this.monotonicClock = monotonicClock;

    this.events =
        new OneToOneRingBuffer(
            new UnsafeBuffer(
                ByteBuffer.allocateDirect(
                    properties.bufferCapacity() + RingBufferDescriptor.TRAILER_LENGTH)));
    this.messageSenderAgent = new MessageSenderAgent();
    this.bestBidAskConflationCache =
        Stream.generate(BestBidAsk::new).limit(instruments.size()).toArray(BestBidAsk[]::new);
    this.bestBidAskSequenceCache = new long[bestBidAskConflationCache.length];
    this.bestBidAskChanged = new boolean[bestBidAskConflationCache.length];
    this.bestBidAskIntervalNanos = properties.bestBidAskInterval().toNanos();
    this.lastTimeSent = monotonicClock.nanoTime();
  }

  @Override
  public void publishTrade(Trade trade, long sequence) {
    int index = events.tryClaim(TRADE, KafkaTradeSlot.LENGTH);
    if (index < 0) {
      kafkaCounters.countDroppedRingFull();
      return;
    }
    KafkaTradeSlot.write(events.buffer(), index, sequence, trade);
    events.commit(index);
  }

  @Override
  public void publishBestBidAsk(BestBidAsk bestBidAsk, long sequence) {
    int index = events.tryClaim(BEST_BID_ASK, KafkaBidAskSlot.LENGTH);
    if (index < 0) {
      kafkaCounters.countDroppedRingFull();
      return;
    }
    KafkaBidAskSlot.write(events.buffer(), index, sequence, bestBidAsk);
    events.commit(index);
  }

  /** Does nothing: heartbeats exist on the fast path only (ADR 0027). */
  @Override
  public void publishHeartbeat(long epochNow, long sequence) {}

  /**
   * Drains up to {@code EVENT_LIMIT} buffered events and, once per interval, sends the tops of book
   * that changed. Returns how many events it read and tops of book it flushed, for the idle
   * strategy. Called by the publisher thread only.
   */
  int sendBufferedEventsToKafka() {
    int eventsRead = events.read(eventHandler, EVENT_LIMIT);
    long monotonicClockTimestamp = monotonicClock.nanoTime();

    // Compared by difference: nanoTime may be negative, and may wrap
    if (monotonicClockTimestamp - lastTimeSent >= bestBidAskIntervalNanos) {
      lastTimeSent = monotonicClockTimestamp;
      eventsRead += sendBestBidAskConflationCached();
    }
    return eventsRead;
  }

  private int sendBestBidAskConflationCached() {
    int eventsFlushed = 0;
    for (int instrumentId = 0; instrumentId < bestBidAskChanged.length; instrumentId++) {
      if (bestBidAskChanged[instrumentId]) {
        bestBidAskChanged[instrumentId] = false;
        eventsFlushed++;
        try {
          sendBestBidAskToKafka(
              bestBidAskConflationCache[instrumentId], bestBidAskSequenceCache[instrumentId]);
        } catch (RuntimeException e) {
          countSendFailure(e);
        }
      }
    }
    return eventsFlushed;
  }

  private void onEvent(int type, MutableDirectBuffer buffer, int index, int length) {
    long sequence = buffer.getLong(index);

    try {
      switch (type) {
        case TRADE -> {
          KafkaTradeSlot.read(buffer, index, instruments, reusedTrade);
          sendTradeToKafka(reusedTrade, sequence);
        }
        case BEST_BID_ASK -> {
          KafkaBidAskSlot.read(buffer, index, instruments, reusedBestBidAsk);
          conflateEvent(reusedBestBidAsk, sequence);
        }
      }
    } catch (RuntimeException e) {
      countSendFailure(e);
    }
  }

  private void countSendFailure(RuntimeException e) {
    kafkaCounters.countFailedSending();
    failureLog.log("Could not hand an event to Kafka", kafkaCounters.failedSending(), e);
  }

  private void conflateEvent(BestBidAsk bestBidAsk, long sequence) {
    int instrumentId = bestBidAsk.instrument().id();
    bestBidAskChanged[instrumentId] = true;
    bestBidAskConflationCache[instrumentId].copyFrom(bestBidAsk);
    bestBidAskSequenceCache[instrumentId] = sequence;
  }

  private void sendTradeToKafka(Trade trade, long sequence) {
    sendMessageToKafka(
        ProtobufMappers.key(trade.instrument()),
        ProtobufMappers.trade(trade, sequence),
        properties.tradeTopicName());
  }

  private void sendBestBidAskToKafka(BestBidAsk bestBidAsk, long sequence) {
    sendMessageToKafka(
        ProtobufMappers.key(bestBidAsk.instrument()),
        ProtobufMappers.bestBidAsk(bestBidAsk, sequence),
        properties.bestBidAskTopicName());
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void sendMessageToKafka(String key, Message message, String topic) {
    kafkaTemplate.send(topic, key, message);
  }

  /**
   * Counts what Kafka acknowledged and what it failed to send. Called on the Kafka producer's I/O
   * thread, so it only touches the thread-safe counters.
   *
   * <p>Generic because Spring Boot injects it into its template as a {@code
   * ProducerListener<Object, Object>}: a bean declared with the template's own types would not
   * match.
   */
  static final class MetricProducerListener<K, V> implements ProducerListener<K, V> {
    private final KafkaCounters counters;
    private final FailureLog failureLog = new FailureLog(LOG, FAILURE_LOG_INTERVAL);

    MetricProducerListener(KafkaCounters counters) {
      this.counters = counters;
    }

    @Override
    public void onSuccess(ProducerRecord<K, V> record, @Nullable RecordMetadata metadata) {
      if (record.value() instanceof com.argus.contracts.proto.market.trades.v1.Trade) {
        this.counters.countTrade();
      } else {
        this.counters.countBestBidAsk();
      }
    }

    @Override
    public void onError(
        @NonNull ProducerRecord<K, V> record,
        @Nullable RecordMetadata metadata,
        @NonNull Exception exception) {
      this.counters.countFailedSending();
      failureLog.log("Kafka did not accept a record", counters.failedSending(), exception);
    }
  }

  private final class MessageSenderAgent implements Agent {
    @Override
    public int doWork() throws Exception {
      return sendBufferedEventsToKafka();
    }

    @Override
    public String roleName() {
      return "kafka-message-sender";
    }

    /**
     * Sends what is left in the ring, then the tops of book still waiting for their interval, and
     * waits for Kafka to take it. Runs once the gateway agent has stopped (the sender stops in a
     * later phase), so nothing is added meanwhile.
     */
    @Override
    public void onClose() {
      while (sendBufferedEventsToKafka() > 0) {
        // Each call sends at most EVENT_LIMIT: repeat until the ring is empty
      }
      sendBestBidAskConflationCached();
      kafkaTemplate.flush();
    }
  }

  /** What it published and dropped. */
  KafkaCounters kafkaCounters() {
    return kafkaCounters;
  }

  Agent messageSenderAgent() {
    return messageSenderAgent;
  }
}
