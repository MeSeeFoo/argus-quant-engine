package com.argus.feedgateway.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.BestBidAsk;
import com.argus.feedgateway.domain.Instrument;
import com.argus.feedgateway.domain.Instruments;
import com.argus.feedgateway.domain.Trade;
import com.google.protobuf.Message;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.List;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.mock.MockProducerFactory;

/** Runs against Kafka's {@code MockProducer}: a real template, no broker. */
class KafkaMarketDataPublisherTest {

  private static final Instruments INSTRUMENTS =
      new Instruments(List.of(new Instrument("BINANCE", "BTCUSDT")));
  // As the relay passes them: from the shard's instruments, with their id
  private static final Instrument BTCUSDT = INSTRUMENTS.get(0);
  private static final String TRADES = "argus.market.trades.v1";
  private static final String QUOTES = "argus.market.quotes.v1";

  // Not auto-completed: each test acknowledges or fails the records itself
  private final MockProducer<String, Message> producer = mockProducer(false);
  private final KafkaCounters counters = new KafkaCounters();
  private final KafkaTemplate<String, Message> template =
      new KafkaTemplate<>(new MockProducerFactory<>(() -> producer));
  private final KafkaMarketDataPublisher publisher = publisher(64 * 1024);

  KafkaMarketDataPublisherTest() {
    template.setProducerListener(new KafkaMarketDataPublisher.MetricProducerListener<>(counters));
  }

  @Test
  void sendsNothingUntilThePublisherThreadDrainsTheRing() {
    publisher.publishTrade(trade(1), 1);

    assertThat(producer.history()).as("the gateway agent only copies into the ring").isEmpty();
    assertThat(publisher.sendBufferedEventsToKafka()).isEqualTo(1);
    assertThat(producer.history()).hasSize(1);
  }

  @Test
  void sendsTradesToTheirTopicKeyedByInstrument() {
    publisher.publishTrade(trade(42), 1);
    publisher.sendBufferedEventsToKafka();

    ProducerRecord<String, Message> trade = producer.history().getFirst();
    assertThat(trade.topic()).isEqualTo(TRADES);
    assertThat(trade.key()).isEqualTo("BINANCE:BTCUSDT");
    assertThat(trade.value())
        .isEqualTo(ProtobufMappers.trade(trade(42), 1))
        .extracting(m -> ((com.argus.contracts.proto.market.trades.v1.Trade) m).getSequence())
        .isEqualTo(1L);
  }

  /** Not every update: the top of book reaches Kafka conflated, with #109. */
  @Test
  void sendsNoTopOfBookYet() {
    publisher.publishBestBidAsk(book(7), 1);

    assertThat(publisher.sendBufferedEventsToKafka()).isZero();
    assertThat(producer.history()).isEmpty();
  }

  @Test
  void drainsInBoundedBatches() {
    for (int i = 1; i <= 100; i++) {
      publisher.publishTrade(trade(i), i);
    }

    assertThat(publisher.sendBufferedEventsToKafka()).isEqualTo(64);
    assertThat(publisher.sendBufferedEventsToKafka()).isEqualTo(36);
    assertThat(publisher.sendBufferedEventsToKafka()).isZero();
  }

  @Test
  void dropsAndCountsWhenTheRingIsFullInsteadOfBlocking() {
    KafkaMarketDataPublisher small = publisher(1024);
    int published = 100;
    for (int i = 1; i <= published; i++) {
      small.publishTrade(trade(i), i);
    }

    int sent = 0;
    for (int drained; (drained = small.sendBufferedEventsToKafka()) > 0; ) {
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
    publisher.sendBufferedEventsToKafka();

    assertThat(counters.trades()).as("nothing acknowledged yet").isZero();
    producer.completeNext();
    producer.completeNext();
    producer.errorNext(new KafkaException("broker unavailable"));

    assertThat(counters.trades()).isEqualTo(2);
    assertThat(counters.failedSending()).isEqualTo(1);
  }

  /** Ready for #109: the listener tells a top of book from a trade by its message. */
  @Test
  void theListenerCountsAnAcknowledgedTopOfBookAsSuch() {
    var listener = new KafkaMarketDataPublisher.MetricProducerListener<String, Message>(counters);

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

    assertThat(publisher.sendBufferedEventsToKafka()).as("both consumed").isEqualTo(2);
    assertThat(counters.failedSending()).isEqualTo(2);

    producer.sendException = null;
    publisher.publishTrade(trade(3), 3);
    publisher.sendBufferedEventsToKafka();
    assertThat(producer.history()).as("the next one goes through").hasSize(1);
  }

  /** On shutdown, after the gateway agent: nothing it queued is left behind. */
  @Test
  void sendsEverythingLeftInTheRingWhenTheSenderCloses() throws Exception {
    int queued = 3 * 64 + 5; // more than one batch
    for (int i = 1; i <= queued; i++) {
      publisher.publishTrade(trade(i), i);
    }

    publisher.messageSenderAgent().onClose();

    assertThat(producer.history()).hasSize(queued);
    assertThat(publisher.sendBufferedEventsToKafka()).isZero();
  }

  @Test
  void neverSendsHeartbeats() {
    publisher.publishHeartbeat(1, 0);

    assertThat(publisher.sendBufferedEventsToKafka()).isZero();
    assertThat(producer.history()).isEmpty();
  }

  /**
   * What runs on the gateway agent's thread: copying into the ring, and dropping when it is full.
   * Only those calls are measured. Draining, which is the sender thread's job and allocates, runs
   * between the measured batches, on this thread but outside the measurement.
   */
  @Test
  void theGatewayAgentsSideAllocatesNothingWhetherTheRingHasRoomOrIsFull() {
    var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    // As the gateway passes them: one reused object of each, refilled for every event
    Trade trade = trade(1);
    BestBidAsk book = book(1);
    int batches = 1_000;
    int batch = 200; // events, about a third of the ring: every one fits

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
    assertThat(counters.droppedRingFull()).as("every trade had room").isZero();
    assertThat(sent).as("the trades, half of the events").isEqualTo(2L * batches * batch / 2);

    // Full: nobody drains, so nearly every event is dropped and counted
    publishEvents(publisher, trade, book, batches * batch); // warm-up
    long before = threads.getCurrentThreadAllocatedBytes();
    publishEvents(publisher, trade, book, batches * batch);
    long whenFull = threads.getCurrentThreadAllocatedBytes() - before;
    assertThat(counters.droppedRingFull()).isPositive();

    assertThat(withRoom).as("bytes allocated with room in the ring").isLessThan(1_024);
    assertThat(whenFull).as("bytes allocated with the ring full").isLessThan(1_024);
  }

  /** Drains the whole ring, as the sender thread would, and forgets what was sent. */
  private long drain() {
    long sent = 0;
    for (int drained; (drained = publisher.sendBufferedEventsToKafka()) > 0; ) {
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

  private KafkaMarketDataPublisher publisher(int capacity) {
    return new KafkaMarketDataPublisher(
        template,
        new KafkaProperties(TRADES, QUOTES, Duration.ofMillis(1), capacity),
        INSTRUMENTS,
        counters);
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
