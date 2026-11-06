package com.argus.feedgateway.adapter.out.kafka;

import com.argus.feedgateway.adapter.out.PublicationMetrics;
import com.argus.feedgateway.domain.Instruments;
import com.argus.feedgateway.utils.AgentLifecycle;
import com.google.protobuf.Message;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.agrona.concurrent.SystemNanoClock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.ProducerListener;

/**
 * The Kafka adapter: the {@code MarketDataPublisher} of the durable path and the thread that sends
 * to Kafka. It owns its properties ({@code argus.feed-gateway.kafka.*}); the {@code KafkaTemplate}
 * comes from Spring Boot, configured with {@code spring.kafka.*}.
 *
 * <p>The counters are a bean of their own because two beans write them: the publisher, and the
 * {@code ProducerListener} that Spring Boot plugs into the template the publisher needs. Taking
 * them from the publisher would make the template depend on the publisher, and the other way round.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KafkaProperties.class)
class KafkaConfiguration {

  private static final String TRANSPORT = "kafka";
  // Below the gateway agent's default phase: the sender starts before it and stops after it, so
  // it drains what the gateway agent queued last
  private static final int SENDER_PHASE = 10;

  @Bean
  KafkaCounters kafkaCounters() {
    return new KafkaCounters();
  }

  /** Plain Java: Spring only creates it. The gateway agent calls it directly (ADR 0010). */
  @Bean
  KafkaMarketDataPublisher kafkaMarketDataPublisher(
      KafkaTemplate<String, Message> kafkaTemplate,
      KafkaProperties properties,
      Instruments instruments,
      KafkaCounters kafkaCounters) {
    return new KafkaMarketDataPublisher(
        kafkaTemplate, properties, instruments, kafkaCounters, new SystemNanoClock());
  }

  /**
   * Replaces Spring Boot's {@code LoggingProducerListener} in the template it creates, with the
   * types it asks for.
   */
  @Bean
  ProducerListener<Object, Object> kafkaProducerListener(KafkaCounters kafkaCounters) {
    return new KafkaMarketDataPublisher.MetricProducerListener<>(kafkaCounters);
  }

  /** The thread that drains the publisher's ring buffer into Kafka, from start to shutdown. */
  @Bean
  AgentLifecycle kafkaSenderAgent(KafkaProperties properties, KafkaMarketDataPublisher publisher) {
    return new AgentLifecycle(
        publisher.messageSenderAgent(),
        // Parks in nanoseconds, so a sleep below a millisecond is not rounded to zero
        new SleepingIdleStrategy(properties.senderIdleSleep().toNanos()),
        SENDER_PHASE);
  }

  @Bean
  MeterBinder kafkaPublicationMetrics(KafkaCounters counters) {
    return registry -> {
      PublicationMetrics.register(registry, counters, TRANSPORT);
      PublicationMetrics.dropped(
          registry, counters, TRANSPORT, "ring_full", KafkaCounters::droppedRingFull);
      PublicationMetrics.dropped(
          registry, counters, TRANSPORT, "send_failed", KafkaCounters::failedSending);
    };
  }
}
