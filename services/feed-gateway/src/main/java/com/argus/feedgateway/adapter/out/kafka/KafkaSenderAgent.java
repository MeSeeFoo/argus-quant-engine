package com.argus.feedgateway.adapter.out.kafka;

import com.argus.feedgateway.adapter.out.kafka.mapping.ProtobufMappers;
import com.argus.feedgateway.domain.BestBidAsk;
import com.argus.feedgateway.domain.Instruments;
import com.argus.feedgateway.domain.Trade;
import com.argus.feedgateway.utils.FailureLog;
import com.google.protobuf.Message;
import java.time.Duration;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.MessageHandler;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * The Kafka adapter's sender thread: the only code that drains the {@link KafkaMarketDataPublisher}
 * ring and calls Kafka. It maps each event to Protobuf and hands it to the {@link KafkaTemplate},
 * which may block or allocate on this thread only.
 *
 * <p>Every trade is sent when drained. A top of book is not: it goes to the {@link
 * BestBidAskConflator}, and every interval the instruments that changed are sent. The interval is
 * measured on a monotonic clock, so the last change still goes out when the market stops.
 */
final class KafkaSenderAgent implements Agent {

  private static final int EVENT_LIMIT = 64;
  private static final Logger LOG = LoggerFactory.getLogger(KafkaSenderAgent.class);
  // While Kafka is down every record fails: the counters count them, the log samples them
  private static final Duration FAILURE_LOG_INTERVAL = Duration.ofSeconds(10);

  private final RingBuffer events;
  private final KafkaTemplate<String, Message> kafkaTemplate;
  private final String tradeTopic;
  private final String bestBidAskTopic;
  private final Instruments instruments;
  private final KafkaCounters counters;
  private final FailureLog failureLog = new FailureLog(LOG, FAILURE_LOG_INTERVAL);

  // Created once, so draining and flushing allocate no lambda
  private final MessageHandler eventHandler = this::onEvent;
  private final BestBidAskConflator.Sink bestBidAskSender = this::sendBestBidAsk;

  // Filled while draining, read before the next event
  private final Trade reusedTrade = new Trade();
  private final BestBidAsk reusedBestBidAsk = new BestBidAsk();
  private final BestBidAskConflator conflator;

  private final NanoClock monotonicClock;
  private final long bestBidAskIntervalNanos;
  private long lastFlush;

  /**
   * Drains {@code events} into {@code kafkaTemplate}. {@code monotonicClock} measures the interval
   * of the top of book.
   *
   * @param events the ring the publisher writes
   * @param kafkaTemplate sends the records, on this agent's thread only
   * @param properties the adapter's properties: topics and the top of book's interval
   * @param instruments the shard's instruments, to read the events' instrument back
   * @param counters the adapter's counters
   * @param monotonicClock a monotonic clock, in nanoseconds
   */
  KafkaSenderAgent(
      RingBuffer events,
      KafkaTemplate<String, Message> kafkaTemplate,
      KafkaProperties properties,
      Instruments instruments,
      KafkaCounters counters,
      NanoClock monotonicClock) {
    this.events = events;
    this.kafkaTemplate = kafkaTemplate;
    this.tradeTopic = properties.tradeTopicName();
    this.bestBidAskTopic = properties.bestBidAskTopicName();
    this.instruments = instruments;
    this.counters = counters;
    this.conflator = new BestBidAskConflator(instruments.size());
    this.monotonicClock = monotonicClock;
    this.bestBidAskIntervalNanos = properties.bestBidAskInterval().toNanos();
    this.lastFlush = monotonicClock.nanoTime();
  }

  /**
   * Drains up to {@code EVENT_LIMIT} buffered events and, once per interval, sends the tops of book
   * that changed.
   *
   * @return how many events it read and tops of book it flushed, for the idle strategy
   */
  @Override
  public int doWork() {
    int work = events.read(eventHandler, EVENT_LIMIT);
    long now = monotonicClock.nanoTime();

    // Compared by difference: nanoTime may be negative, and may wrap
    if (now - lastFlush >= bestBidAskIntervalNanos) {
      lastFlush = now;
      work += conflator.flush(bestBidAskSender);
    }
    return work;
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
    while (doWork() > 0) {
      // Each call sends at most EVENT_LIMIT: repeat until the ring is empty
    }
    conflator.flush(bestBidAskSender);
    kafkaTemplate.flush();
  }

  /**
   * Handles one slot of the ring: sends a trade, conflates a top of book.
   *
   * @param type the slot's type, {@link TradeCodec#TYPE} or {@link BestBidAskCodec#TYPE}
   * @param buffer the ring buffer
   * @param index where the slot starts
   * @param length the length of the slot in bytes
   */
  private void onEvent(int type, MutableDirectBuffer buffer, int index, int length) {
    long sequence = buffer.getLong(index);
    try {
      switch (type) {
        case TradeCodec.TYPE -> {
          TradeCodec.read(buffer, index, instruments, reusedTrade);
          send(
              tradeTopic,
              ProtobufMappers.key(reusedTrade.instrument()),
              ProtobufMappers.trade(reusedTrade, sequence));
        }
        case BestBidAskCodec.TYPE -> {
          BestBidAskCodec.read(buffer, index, instruments, reusedBestBidAsk);
          conflator.conflate(reusedBestBidAsk, sequence);
        }
        default -> {
          // The publisher writes no other type
        }
      }
    } catch (RuntimeException e) {
      countSendFailure(e);
    }
  }

  /**
   * Sends a conflated top of book. A failure is counted and does not stop the others.
   *
   * @param bestBidAsk the top of book
   * @param sequence the sequence it was published with
   */
  private void sendBestBidAsk(BestBidAsk bestBidAsk, long sequence) {
    try {
      send(
          bestBidAskTopic,
          ProtobufMappers.key(bestBidAsk.instrument()),
          ProtobufMappers.bestBidAsk(bestBidAsk, sequence));
    } catch (RuntimeException e) {
      countSendFailure(e);
    }
  }

  /**
   * Hands a record to the template; the {@link CountingProducerListener} counts how it ends.
   *
   * @param topic the topic
   * @param key the record key
   * @param message the record value
   */
  @SuppressWarnings("FutureReturnValueIgnored")
  private void send(String topic, String key, Message message) {
    kafkaTemplate.send(topic, key, message);
  }

  /**
   * Counts an event that could not be handed to Kafka, and logs a sample of them.
   *
   * @param e why it failed
   */
  private void countSendFailure(RuntimeException e) {
    counters.countFailedSending();
    failureLog.log("Could not hand an event to Kafka", counters.failedSending(), e);
  }
}
