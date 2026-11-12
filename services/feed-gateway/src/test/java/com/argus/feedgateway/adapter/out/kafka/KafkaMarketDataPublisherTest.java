package com.argus.feedgateway.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.argus.feedgateway.adapter.out.kafka.mapping.ProtobufMappers;
import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.BestBidAsk;
import com.argus.feedgateway.domain.Instrument;
import com.argus.feedgateway.domain.Instruments;
import com.argus.feedgateway.domain.Trade;
import com.google.protobuf.Message;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.List;
import org.agrona.concurrent.NanoClock;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.mock.MockProducerFactory;

/**
 * The publisher and its sender together, as the adapter runs them, against Kafka's {@code
 * MockProducer}: a real template, no broker. Both run on the test's thread.
 */
class KafkaMarketDataPublisherTest {

  private static final Instruments INSTRUMENTS =
      new Instruments(List.of(new Instrument("BINANCE", "BTCUSDT")));
  // As the relay passes them: from the shard's instruments, with their id
  private static final Instrument BTCUSDT = INSTRUMENTS.get(0);
  private static final String TRADES = "argus.market.trades.v1";
  private static final String QUOTES = "argus.market.quotes.v1";
  private static final Duration INTERVAL = Duration.ofSeconds(1);

  // What the sender's clock says: only the test moves it
  private long nanos;
  private final NanoClock clock = () -> nanos;
  // Not auto-completed: each test acknowledges or fails the records itself
  private final MockProducer<String, Message> producer = mockProducer(false);
  private final KafkaCounters counters = new KafkaCounters();
  private final KafkaTemplate<String, Message> template =
      new KafkaTemplate<>(new MockProducerFactory<>(() -> producer));
  // A pair over one ring; a test that needs other instruments or capacity connects a new one
  private KafkaMarketDataPublisher publisher;
  private KafkaSenderAgent sender;

  KafkaMarketDataPublisherTest() {
    template.setProducerListener(new CountingProducerListener<>(counters));
    connect(INSTRUMENTS, 64 * 1024);
  }

  @Test
  void sendsNothingUntilThePublisherThreadDrainsTheRing() {
    publisher.publishTrade(trade(1), 1);

    assertThat(producer.history()).as("the gateway agent only copies into the ring").isEmpty();
    assertThat(sender.doWork()).isEqualTo(1);
    assertThat(producer.history()).hasSize(1);
  }

  @Test
  void sendsTradesToTheirTopicKeyedByInstrument() {
    publisher.publishTrade(trade(42), 1);
    sender.doWork();

    ProducerRecord<String, Message> trade = producer.history().getFirst();
    assertThat(trade.topic()).isEqualTo(TRADES);
    assertThat(trade.key()).isEqualTo("BINANCE:BTCUSDT");
    assertThat(trade.value())
        .isEqualTo(ProtobufMappers.trade(trade(42), 1))
        .extracting(m -> ((com.argus.contracts.proto.market.trades.v1.Trade) m).getSequence())
        .isEqualTo(1L);
  }

  @Test
  void sendsTheTopOfBookToItsTopicKeyedByInstrument() {
    publisher.publishBestBidAsk(book(7), 1);
    sender.doWork();

    assertThat(producer.history()).as("it waits for the interval").isEmpty();
    nanos += INTERVAL.toNanos();
    assertThat(sender.doWork()).as("no more events, one flushed").isEqualTo(1);

    ProducerRecord<String, Message> book = producer.history().getFirst();
    assertThat(book.topic()).isEqualTo(QUOTES);
    assertThat(book.key()).isEqualTo("BINANCE:BTCUSDT");
    assertThat(book.value()).isEqualTo(ProtobufMappers.bestBidAsk(book(7), 1));
  }

  @Test
  void sendsOnlyTheLatestTopOfBookOfAnIntervalWithItsOwnSequence() {
    publisher.publishBestBidAsk(book(1), 10);
    publisher.publishBestBidAsk(book(2), 11);
    publisher.publishBestBidAsk(book(3), 12);
    sender.doWork();

    nanos += INTERVAL.toNanos();
    sender.doWork();

    assertThat(producer.history())
        .singleElement()
        .extracting(ProducerRecord::value)
        .isEqualTo(ProtobufMappers.bestBidAsk(book(3), 12));
  }

  @Test
  void sendsNoTopOfBookIfNothingChangedSinceTheLastOne() {
    publisher.publishBestBidAsk(book(1), 1);
    sender.doWork();
    nanos += INTERVAL.toNanos();
    sender.doWork();

    nanos += 5 * INTERVAL.toNanos();

    assertThat(sender.doWork()).isZero();
    assertThat(producer.history()).hasSize(1);
  }

  @Test
  void sendsATopOfBookAtMostOncePerInterval() {
    nanos += INTERVAL.toNanos();
    sender.doWork(); // the interval starts here, with nothing to send

    publisher.publishBestBidAsk(book(1), 1);
    nanos += INTERVAL.toNanos() - 1;
    sender.doWork();
    assertThat(producer.history()).as("one nanosecond early").isEmpty();

    nanos += 1;
    sender.doWork();
    assertThat(producer.history()).hasSize(1);
  }

  @Test
  void measuresTheIntervalWhateverValueTheClockStartsAt() {
    nanos = -5 * INTERVAL.toNanos(); // nanoTime may well be negative
    connect(INSTRUMENTS, 64 * 1024);
    publisher.publishBestBidAsk(book(1), 1);

    sender.doWork();
    assertThat(producer.history()).isEmpty();

    nanos += INTERVAL.toNanos();
    sender.doWork();
    assertThat(producer.history()).hasSize(1);
  }

  @Test
  void conflatesEachInstrumentIndependently() {
    var instruments =
        new Instruments(
            List.of(new Instrument("BINANCE", "BTCUSDT"), new Instrument("BINANCE", "ETHUSDT")));
    connect(instruments, 64 * 1024);
    BestBidAsk latestBtc = book(2).instrument(instruments.get(0));
    BestBidAsk latestEth = book(3).instrument(instruments.get(1));
    publisher.publishBestBidAsk(book(1).instrument(instruments.get(0)), 1);
    publisher.publishBestBidAsk(latestBtc, 2);
    publisher.publishBestBidAsk(latestEth, 3);
    sender.doWork();

    nanos += INTERVAL.toNanos();
    sender.doWork();

    assertThat(producer.history())
        .extracting(ProducerRecord::key, ProducerRecord::value)
        .containsExactly(
            tuple("BINANCE:BTCUSDT", ProtobufMappers.bestBidAsk(latestBtc, 2)),
            tuple("BINANCE:ETHUSDT", ProtobufMappers.bestBidAsk(latestEth, 3)));

    // Only the one that changes goes out next
    publisher.publishBestBidAsk(latestEth.bookUpdateId(4), 4);
    sender.doWork();
    nanos += INTERVAL.toNanos();
    sender.doWork();

    assertThat(producer.history()).hasSize(3);
    assertThat(producer.history().getLast().key()).isEqualTo("BINANCE:ETHUSDT");
  }

  @Test
  void aTradeIsNeverConflatedNorHeldBackByATopOfBook() {
    publisher.publishBestBidAsk(book(1), 1);
    publisher.publishTrade(trade(1), 2);
    publisher.publishTrade(trade(2), 3);

    sender.doWork();

    assertThat(producer.history())
        .extracting(ProducerRecord::topic)
        .containsExactly(TRADES, TRADES);
  }

  @Test
  void countsATopOfBookThatCannotBeHandedToKafkaAndGoesOn() {
    publisher.publishBestBidAsk(book(1), 1);
    sender.doWork();
    producer.sendException = new KafkaException("serialization failed");

    nanos += INTERVAL.toNanos();
    sender.doWork();

    assertThat(counters.failedSending()).isEqualTo(1);

    producer.sendException = null;
    publisher.publishBestBidAsk(book(2), 2);
    sender.doWork();
    nanos += INTERVAL.toNanos();
    sender.doWork();
    assertThat(producer.history()).as("the next one goes through").hasSize(1);
  }

  /** On shutdown, without waiting for the interval: the last change is not lost. */
  @Test
  void sendsTheTopOfBookStillWaitingWhenTheSenderCloses() throws Exception {
    publisher.publishBestBidAsk(book(1), 1);
    publisher.publishBestBidAsk(book(2), 2);

    sender.onClose();

    assertThat(producer.history())
        .singleElement()
        .extracting(ProducerRecord::value)
        .isEqualTo(ProtobufMappers.bestBidAsk(book(2), 2));
  }

  /**
   * Draining updates into the slots, and a flush with nothing to send. Only the flush that does
   * send allocates, at most once per instrument and interval, to build its messages.
   */
  @Test
  void conflatingAllocatesNothing() {
    var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    BestBidAsk book = book(1);
    int batches = 1_000;
    int batch = 64;

    long conflating = 0;
    for (int warmUp = 0; warmUp < 2; warmUp++) {
      conflating = 0;
      for (int i = 0; i < batches; i++) {
        for (int update = 1; update <= batch; update++) {
          publisher.publishBestBidAsk(book.bookUpdateId(update), update);
        }
        long before = threads.getCurrentThreadAllocatedBytes();
        // Until empty: where the ring wraps, one call reads less than a whole batch
        while (sender.doWork() > 0) {}
        conflating += threads.getCurrentThreadAllocatedBytes() - before;
      }
    }
    assertThat(producer.history()).as("the clock never moved").isEmpty();

    nanos += INTERVAL.toNanos();
    sender.doWork(); // sends the latest, which may allocate

    int flushes = 10_000;
    long emptyFlushes = 0;
    for (int i = 0; i < flushes; i++) {
      nanos += INTERVAL.toNanos();
      long before = threads.getCurrentThreadAllocatedBytes();
      sender.doWork();
      emptyFlushes += threads.getCurrentThreadAllocatedBytes() - before;
    }
    assertThat(producer.history()).as("nothing else changed").hasSize(1);

    // Under one byte each, as the smallest object takes 16: see the gateway agent's test below
    assertThat(conflating)
        .as("bytes allocated copying into the slots")
        .isLessThan((long) batches * batch);
    assertThat(emptyFlushes).as("bytes allocated flushing nothing").isLessThan(flushes);
  }

  @Test
  void drainsInBoundedBatches() {
    for (int i = 1; i <= 100; i++) {
      publisher.publishTrade(trade(i), i);
    }

    assertThat(sender.doWork()).isEqualTo(64);
    assertThat(sender.doWork()).isEqualTo(36);
    assertThat(sender.doWork()).isZero();
  }

  @Test
  void dropsAndCountsWhenTheRingIsFullInsteadOfBlocking() {
    connect(INSTRUMENTS, 1024);
    int published = 100;
    for (int i = 1; i <= published; i++) {
      publisher.publishTrade(trade(i), i);
    }

    int sent = 0;
    for (int drained; (drained = sender.doWork()) > 0; ) {
      sent += drained;
    }
    assertThat(counters.droppedRingFull()).isPositive();
    assertThat(sent + counters.droppedRingFull()).isEqualTo(published);
  }

  @Test
  void countsWhatKafkaAcknowledgesAndWhatItFails() {
    publisher.publishTrade(trade(1), 1);
    publisher.publishTrade(trade(2), 2);
    publisher.publishTrade(trade(3), 3);
    sender.doWork();

    assertThat(counters.trades()).as("nothing acknowledged yet").isZero();
    producer.completeNext();
    producer.completeNext();
    producer.errorNext(new KafkaException("broker unavailable"));

    assertThat(counters.trades()).isEqualTo(2);
    assertThat(counters.failedSending()).isEqualTo(1);
  }

  /** The listener tells a top of book from a trade by its message. */
  @Test
  void theListenerCountsAnAcknowledgedTopOfBookAsSuch() {
    var listener = new CountingProducerListener<String, Message>(counters);

    listener.onSuccess(
        new ProducerRecord<>(QUOTES, "BINANCE:BTCUSDT", ProtobufMappers.bestBidAsk(book(1), 1)),
        null);

    assertThat(counters.bestBidAsks()).isEqualTo(1);
    assertThat(counters.trades()).isZero();
  }

  @Test
  void countsAnEventThatCannotBeHandedToKafkaAndGoesOn() {
    publisher.publishTrade(trade(1), 1);
    publisher.publishTrade(trade(2), 2);
    producer.sendException = new KafkaException("serialization failed");

    assertThat(sender.doWork()).as("both consumed").isEqualTo(2);
    assertThat(counters.failedSending()).isEqualTo(2);

    producer.sendException = null;
    publisher.publishTrade(trade(3), 3);
    sender.doWork();
    assertThat(producer.history()).as("the next one goes through").hasSize(1);
  }

  /** On shutdown, after the gateway agent: nothing it queued is left behind. */
  @Test
  void sendsEverythingLeftInTheRingWhenTheSenderCloses() throws Exception {
    int queued = 3 * 64 + 5; // more than one batch
    for (int i = 1; i <= queued; i++) {
      publisher.publishTrade(trade(i), i);
    }

    sender.onClose();

    assertThat(producer.history()).hasSize(queued);
    assertThat(sender.doWork()).isZero();
  }

  @Test
  void neverSendsHeartbeats() {
    publisher.publishHeartbeat(1, 0);

    assertThat(sender.doWork()).isZero();
    assertThat(producer.history()).isEmpty();
  }

  /**
   * What runs on the gateway agent's thread: copying into the ring, and dropping when it is full.
   * Only those calls are measured. Draining, which is the sender thread's job and allocates, runs
   * between the measured batches, on this thread but outside the measurement.
   *
   * <p>The limit is under one byte per event, as the smallest object takes 16: a fixed number of
   * bytes would fail on what the JVM itself allocates now and then on the thread.
   */
  @Test
  void theGatewayAgentsSideAllocatesNothingWhetherTheRingHasRoomOrIsFull() {
    var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    // As the gateway passes them: one reused object of each, refilled for every event
    Trade trade = trade(1);
    BestBidAsk book = book(1);
    int batches = 1_000;
    int batch = 200; // events, about a quarter of the ring: every one fits

    // With room: fill part of the ring, measured, then drain it, not measured
    long withRoom = 0;
    long sent = 0;
    for (int warmUp = 0; warmUp < 2; warmUp++) {
      withRoom = 0;
      for (int i = 0; i < batches; i++) {
        long before = threads.getCurrentThreadAllocatedBytes();
        publishEvents(publisher, trade, book, batch);
        withRoom += threads.getCurrentThreadAllocatedBytes() - before;
        sent += drain();
      }
    }
    assertThat(counters.droppedRingFull()).as("every event had room").isZero();
    assertThat(sent).as("every event").isEqualTo(2L * batches * batch);

    // Full: nobody drains, so nearly every event is dropped and counted
    publishEvents(publisher, trade, book, batches * batch); // warm-up
    long before = threads.getCurrentThreadAllocatedBytes();
    publishEvents(publisher, trade, book, batches * batch);
    long whenFull = threads.getCurrentThreadAllocatedBytes() - before;
    assertThat(counters.droppedRingFull()).isPositive();

    long events = (long) batches * batch;
    assertThat(withRoom).as("bytes allocated with room in the ring").isLessThan(events);
    assertThat(whenFull).as("bytes allocated with the ring full").isLessThan(events);
  }

  /** Drains the whole ring, as the sender thread would, and forgets what was sent. */
  private long drain() {
    long sent = 0;
    for (int drained; (drained = sender.doWork()) > 0; ) {
      sent += drained;
    }
    producer.clear();
    return sent;
  }

  private static void publishEvents(
      KafkaMarketDataPublisher publisher, Trade trade, BestBidAsk book, int events) {
    for (int i = 0; i < events; i += 2) {
      publisher.publishTrade(trade.tradeId(i), i + 1);
      publisher.publishBestBidAsk(book.bookUpdateId(i), i + 2);
    }
  }

  /**
   * A {@code MockProducer} that ignores {@code close}: with {@code MockProducerFactory}, the
   * template closes its producer after a completed send, and every later send would then fail.
   */
  private static MockProducer<String, Message> mockProducer(boolean autoComplete) {
    return new MockProducer<>(
        autoComplete, null, new StringSerializer(), (topic, message) -> message.toByteArray()) {
      @Override
      public void close() {}

      @Override
      public void close(Duration timeout) {}
    };
  }

  /** Replaces the publisher and its sender with a new pair, over a ring of {@code capacity}. */
  private void connect(Instruments instruments, int capacity) {
    var properties = new KafkaProperties(TRADES, QUOTES, Duration.ofMillis(1), capacity, INTERVAL);
    publisher = new KafkaMarketDataPublisher(capacity, counters);
    sender =
        new KafkaSenderAgent(
            publisher.events(), template, properties, instruments, counters, clock);
  }

  private static Trade trade(long tradeId) {
    return new Trade()
        .instrument(BTCUSDT)
        .tradeId(tradeId)
        .price(6_421_050, -2)
        .quantity(12, -3)
        .aggressorSide(AggressorSide.BUY)
        .exchangeTimestamp(11)
        .receiveTimestamp(12);
  }

  private static BestBidAsk book(long bookUpdateId) {
    return new BestBidAsk()
        .instrument(BTCUSDT)
        .bookUpdateId(bookUpdateId)
        .bid(6_421_050, -2, 3, -3)
        .ask(6_421_051, -2, 4, -3)
        .exchangeTimestamp(21)
        .receiveTimestamp(22);
  }
}
