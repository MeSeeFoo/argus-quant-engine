package com.argus.feedgateway.adapter.out.kafka;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the gateway publishes market data on the durable path (ADR 0027). The connection to Kafka
 * and Apicurio is Spring Boot's own {@code spring.kafka.*}.
 *
 * @param tradeTopicName topic of every {@code Trade}
 * @param bestBidAskTopicName topic of the conflated {@code BestBidAsk}
 * @param senderIdleSleep how long the Kafka sender parks when the ring buffer is empty: the most an
 *     event waits before it is handed to Kafka
 * @param bufferCapacity bytes of the ring buffer between the gateway agent and the Kafka sender, a
 *     power of two
 * @param bestBidAskInterval how often, at most, the latest top of book of each instrument is
 *     published, and only if it changed (ADR 0027). Measured on a monotonic clock read by the Kafka
 *     sender: it limits the rate, it is not event time
 */
@ConfigurationProperties("argus.feed-gateway.kafka")
public record KafkaProperties(
    String tradeTopicName,
    String bestBidAskTopicName,
    @DefaultValue("5ms") Duration senderIdleSleep,
    int bufferCapacity,
    @DefaultValue("1s") Duration bestBidAskInterval) {

  /** Fails at startup with the property's name, instead of later with a less clear message. */
  public KafkaProperties {
    if (bufferCapacity <= 0 || Integer.bitCount(bufferCapacity) != 1) {
      throw new IllegalArgumentException(
          "argus.feed-gateway.kafka.buffer-capacity must be a power of two: " + bufferCapacity);
    }
    if (senderIdleSleep.isNegative()) {
      throw new IllegalArgumentException(
          "argus.feed-gateway.kafka.sender-idle-sleep must not be negative: " + senderIdleSleep);
    }
    if (!bestBidAskInterval.isPositive()) {
      throw new IllegalArgumentException(
          "argus.feed-gateway.kafka.best-bid-ask-interval must be positive: " + bestBidAskInterval);
    }
  }
}
