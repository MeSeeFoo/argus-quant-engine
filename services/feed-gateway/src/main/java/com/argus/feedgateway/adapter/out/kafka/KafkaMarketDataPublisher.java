package com.argus.feedgateway.adapter.out.kafka;

import com.argus.feedgateway.application.port.out.MarketDataPublisher;
import com.argus.feedgateway.domain.BestBidAsk;
import com.argus.feedgateway.domain.Instruments;
import com.argus.feedgateway.domain.Trade;
import com.argus.feedgateway.utils.FailureLog;
import com.google.protobuf.Message;
import java.nio.ByteBuffer;
import java.time.Duration;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.MessageHandler;
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
 * The durable path's {@link MarketDataPublisher}: publishes every trade to Kafka as a canonical
 * Protobuf message, without ever blocking the gateway agent (ADR 0005, ADR 0027). The conflated top
 * of book arrives with #109.
 *
 * <p>Two threads, one ring buffer between them. The gateway agent only copies each trade into the
 * ring ({@link KafkaTradeSlot}), without allocating, and drops and counts it when the ring is full.
 * The publisher thread drains the ring, maps each trade to Protobuf and hands it to the {@link
 * KafkaTemplate}, which may block or allocate on that thread only.
 */
public final class KafkaMarketDataPublisher implements MarketDataPublisher {

  private static final int TRADE = 1;
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

  /**
   * Sends with {@code kafkaTemplate}, through a ring buffer of {@code properties.bufferCapacity()}
   * bytes, the trades of {@code instruments}: the shard's, which the relay's trades come from.
   */
  public KafkaMarketDataPublisher(
      KafkaTemplate<String, Message> kafkaTemplate,
      KafkaProperties properties,
      Instruments instruments,
      KafkaCounters kafkaCounters) {
    this.kafkaTemplate = kafkaTemplate;
    this.properties = properties;
    this.instruments = instruments;
    this.events =
        new OneToOneRingBuffer(
            new UnsafeBuffer(
                ByteBuffer.allocateDirect(
                    properties.bufferCapacity() + RingBufferDescriptor.TRAILER_LENGTH)));
    this.kafkaCounters = kafkaCounters;
    this.messageSenderAgent = new MessageSenderAgent();
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

  /**
   * Does nothing yet: the top of book reaches Kafka conflated, the latest per instrument at most
   * once per interval (ADR 0027, #109). Sending every update until then would put on {@code
   * argus.market.quotes.v1} what the topic must never carry.
   */
  @Override
  public void publishBestBidAsk(BestBidAsk bestBidAsk, long sequence) {}

  /** Does nothing: heartbeats exist on the fast path only (ADR 0027). */
  @Override
  public void publishHeartbeat(long epochNow, long sequence) {}

  /**
   * Sends up to {@code EVENT_LIMIT} buffered events to Kafka, and returns how many. Called by the
   * publisher thread only.
   */
  int sendBufferedEventsToKafka() {
    return events.read(eventHandler, EVENT_LIMIT);
  }

  private void onEvent(int type, MutableDirectBuffer buffer, int index, int length) {
    // Only trades go through the ring; the conflated top of book will not (#109)
    long sequence = buffer.getLong(index);
    try {
      KafkaTradeSlot.read(buffer, index, instruments, reusedTrade);
      sendTradeToKafka(reusedTrade, sequence);
    } catch (RuntimeException e) {
      kafkaCounters.countFailedSending();
      failureLog.log("Could not hand an event to Kafka", kafkaCounters.failedSending(), e);
    }
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void sendTradeToKafka(Trade trade, long sequence) {
    kafkaTemplate.send(
        properties.tradeTopicName(),
        ProtobufMappers.key(trade.instrument()),
        ProtobufMappers.trade(trade, sequence));
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
     * Sends what is left in the ring and waits for Kafka to take it. Runs once the gateway agent
     * has stopped (the sender stops in a later phase), so nothing is added meanwhile.
     */
    @Override
    public void onClose() {
      while (sendBufferedEventsToKafka() > 0) {
        // Each call sends at most EVENT_LIMIT: repeat until the ring is empty
      }
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
