package com.argus.feedgateway.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.argus.contracts.proto.market.trades.v1.Side;
import com.argus.feedgateway.application.MarketDataCounters;
import com.argus.feedgateway.application.MarketDataRelay;
import com.argus.feedgateway.application.RecordingPublisher;
import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.Instrument;
import com.argus.feedgateway.domain.Instruments;
import com.argus.feedgateway.domain.Trade;
import com.google.protobuf.Message;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.agrona.concurrent.SystemNanoClock;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Trades from the relay to a real Kafka and back, with their schema registered in a real Apicurio:
 * the same images as {@code infra/docker}. Next to the Kafka publisher, a {@link
 * RecordingPublisher} gets what the fast path gets, so each record is compared with the trade and
 * sequence that Aeron encodes ({@code AeronMarketDataPublisherTest} checks that encoding).
 *
 * <p>Needs Docker: skipped without it, run by CI.
 */
@Testcontainers(disabledWithoutDocker = true)
class KafkaRoundTripTest {

  private static final String TRADES = "argus.market.trades.v1";
  private static final String QUOTES = "argus.market.quotes.v1";
  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Container private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.1");

  @Container
  private static final GenericContainer<?> APICURIO =
      new GenericContainer<>("apicurio/apicurio-registry:3.3.3")
          .withExposedPorts(8080)
          .waitingFor(Wait.forHttp("/apis/registry/v3/system/info").forStatusCode(200));

  @Test
  void kafkaReceivesEveryTradeAsTheFastPathGetsItSequenceIncluded() throws Exception {
    // Topics are declared, never created on first use (infra/docker/kafka/topics.conf)
    try (Admin admin =
        Admin.create(
            Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
      admin.createTopics(List.of(new NewTopic(TRADES, 1, (short) 1))).all().get();
    }
    var factory =
        new DefaultKafkaProducerFactory<String, Message>(
            Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                ProtobufKafkaSerializer.class,
                "apicurio.registry.url",
                registryUrl(),
                "apicurio.registry.auto-register",
                true));
    var counters = new KafkaCounters();
    var template = new KafkaTemplate<>(factory);
    template.setProducerListener(new KafkaMarketDataPublisher.MetricProducerListener<>(counters));
    var instruments = new Instruments(List.of(new Instrument("BINANCE", "BTCUSDT")));
    var kafka =
        new KafkaMarketDataPublisher(
            template,
            new KafkaProperties(
                TRADES, QUOTES, Duration.ofMillis(1), 64 * 1024, Duration.ofSeconds(1)),
            instruments,
            counters,
            new SystemNanoClock());
    var fastPath = new RecordingPublisher();
    var relay =
        new MarketDataRelay(
            instruments, List.of(fastPath, kafka), new MarketDataCounters(), 1_000_000_000L);
    Trade trade = new Trade().instrument(instruments.get(0));
    int trades = 100;

    var sender =
        new AgentRunner(
            new SleepingIdleStrategy(Duration.ofMillis(1).toNanos()),
            Throwable::printStackTrace,
            null,
            kafka.messageSenderAgent());
    AgentRunner.startOnThread(sender);
    List<ConsumerRecord<String, Message>> received;
    try {
      for (int i = 1; i <= trades; i++) {
        relay.sendTrade(
            trade
                .tradeId(1000 + i)
                .price(6_421_050 + i, -2)
                .quantity(i, -3)
                .aggressorSide(i % 2 == 0 ? AggressorSide.BUY : AggressorSide.SELL)
                .exchangeTimestamp(1_760_000_000_000_000_000L + i)
                .receiveTimestamp(1_760_000_000_000_500_000L + i));
      }
      received = consume(trades);
    } finally {
      sender.close();
      factory.destroy();
    }

    assertThat(received).hasSize(trades);
    for (int i = 0; i < trades; i++) {
      ConsumerRecord<String, Message> record = received.get(i);
      RecordingPublisher.Trade expected = fastPath.trades.get(i);
      var actual =
          com.argus.contracts.proto.market.trades.v1.Trade.parseFrom(record.value().toByteString());

      assertThat(record.key()).isEqualTo("BINANCE:BTCUSDT");
      assertThat(actual.getSequence())
          .as("the fast path's sequence")
          .isEqualTo(fastPath.sequences.get(i));
      assertThat(actual.getTradeId()).isEqualTo(expected.tradeId());
      assertThat(actual.getPrice()).isEqualTo(expected.price());
      assertThat(actual.getQuantity()).isEqualTo(expected.quantity());
      assertThat(actual.getAggressorSide())
          .isEqualTo(
              expected.aggressorSide() == AggressorSide.BUY ? Side.SIDE_BUY : Side.SIDE_SELL);
      assertThat(actual.getExchangeTimestamp()).isEqualTo(expected.exchangeTimestamp());
      assertThat(actual.getReceiveTimestamp()).isEqualTo(expected.receiveTimestamp());
      assertThat(actual.getVenue()).isEqualTo("BINANCE");
      assertThat(actual.getSymbol()).isEqualTo("BTCUSDT");
    }
    assertThat(counters.trades()).as("every record acknowledged").isEqualTo(trades);
    assertThat(counters.failedSending()).isZero();
  }

  private static List<ConsumerRecord<String, Message>> consume(int expected) {
    var received = new ArrayList<ConsumerRecord<String, Message>>();
    try (var consumer =
        new KafkaConsumer<String, Message>(
            Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "round-trip-test",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ProtobufKafkaDeserializer.class,
                "apicurio.registry.url",
                registryUrl()))) {
      consumer.subscribe(List.of(TRADES));
      long deadline = System.nanoTime() + TIMEOUT.toNanos();
      while (received.size() < expected && System.nanoTime() < deadline) {
        consumer.poll(Duration.ofMillis(200)).forEach(received::add);
      }
    }
    return received;
  }

  private static String registryUrl() {
    return "http://"
        + APICURIO.getHost()
        + ":"
        + APICURIO.getMappedPort(8080)
        + "/apis/registry/v3";
  }
}
