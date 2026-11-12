package com.argus.feedgateway.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.argus.feedgateway.application.MarketDataCounters;
import com.argus.feedgateway.application.MarketDataRelay;
import com.argus.feedgateway.application.RecordingPublisher;
import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.Instrument;
import com.argus.feedgateway.domain.Instruments;
import com.argus.feedgateway.domain.Trade;
import com.google.protobuf.Message;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.agrona.concurrent.SystemNanoClock;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Kafka down: a real producer pointed at a closed port, so every send blocks waiting for the
 * cluster. The fast path, here a {@link RecordingPublisher} beside the Kafka publisher in the same
 * relay, must not notice.
 */
class KafkaUnreachableTest {

  // Each send blocks this long waiting for metadata: far more than the whole test may take
  private static final Duration MAX_BLOCK = Duration.ofSeconds(3);
  private static final Duration FAST_PATH_BUDGET = Duration.ofSeconds(1);

  @Test
  void theFastPathKeepsItsPaceAndKafkaDropsAreCounted() {
    Serializer<Message> protobuf = (topic, message) -> message.toByteArray();
    var factory =
        new DefaultKafkaProducerFactory<String, Message>(
            Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                "localhost:1",
                ProducerConfig.MAX_BLOCK_MS_CONFIG,
                (int) MAX_BLOCK.toMillis()),
            new StringSerializer(),
            protobuf);
    factory.setPhysicalCloseTimeout(1);
    var counters = new KafkaCounters();
    var template = new KafkaTemplate<>(factory);
    template.setProducerListener(new CountingProducerListener<>(counters));
    var instruments = new Instruments(List.of(new Instrument("BINANCE", "BTCUSDT")));
    var kafka =
        new KafkaMarketDataPublisher(
            template,
            // A small ring, so it fills while the sender is stuck
            new KafkaProperties(
                "trades", "quotes", Duration.ofMillis(1), 4 * 1024, Duration.ofSeconds(1)),
            instruments,
            counters,
            new SystemNanoClock());
    var fastPath = new RecordingPublisher();
    var relay =
        new MarketDataRelay(
            instruments, List.of(fastPath, kafka), new MarketDataCounters(), 1_000_000_000L);
    Trade trade =
        new Trade()
            .instrument(instruments.get(0))
            .price(1, 0)
            .quantity(1, 0)
            .aggressorSide(AggressorSide.BUY);
    int trades = 10_000;

    var sender =
        new AgentRunner(
            new SleepingIdleStrategy(Duration.ofMillis(1).toNanos()),
            Throwable::printStackTrace,
            null,
            kafka.messageSenderAgent());
    AgentRunner.startOnThread(sender);
    long elapsed;
    try {
      long start = System.nanoTime();
      for (int i = 1; i <= trades; i++) {
        relay.sendTrade(trade.tradeId(i));
      }
      elapsed = System.nanoTime() - start;
    } finally {
      sender.close();
      factory.destroy();
    }

    assertThat(Duration.ofNanos(elapsed))
        .as("the relay never waited on Kafka: one wait alone would take %s", MAX_BLOCK)
        .isLessThan(FAST_PATH_BUDGET);
    assertThat(fastPath.trades).as("the fast path got every trade").hasSize(trades);
    assertThat(counters.droppedRingFull()).as("Kafka's side dropped and counted").isPositive();
    assertThat(counters.trades()).as("nothing reached Kafka").isZero();
  }
}
