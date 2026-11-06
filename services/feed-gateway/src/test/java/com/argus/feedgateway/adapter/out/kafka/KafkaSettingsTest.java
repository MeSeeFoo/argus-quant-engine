package com.argus.feedgateway.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer;
import java.time.Duration;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/** The settings in application.yml name classes that exist and the topics of topics.conf. */
class KafkaSettingsTest {

  @Test
  void serializesKeysAsTextAndValuesAsProtobufWithApicurio() throws Exception {
    PropertySource<?> yaml = applicationYaml();

    assertThat(yaml.getProperty("spring.kafka.producer.key-serializer"))
        .isEqualTo(StringSerializer.class.getName());
    assertThat(yaml.getProperty("spring.kafka.producer.value-serializer"))
        .isEqualTo(ProtobufKafkaSerializer.class.getName());
  }

  @Test
  void publishesToTheMarketDataTopics() throws Exception {
    PropertySource<?> yaml = applicationYaml();

    assertThat(yaml.getProperty("argus.feed-gateway.kafka.trade-topic-name"))
        .isEqualTo("argus.market.trades.v1");
    assertThat(yaml.getProperty("argus.feed-gateway.kafka.best-bid-ask-topic-name"))
        .isEqualTo("argus.market.quotes.v1");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1024, 1000})
  void rejectsABufferCapacityThatIsNotAPowerOfTwo(int capacity) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> properties(Duration.ofMillis(1), capacity, Duration.ofSeconds(1)))
        .withMessageContaining("buffer-capacity");
  }

  @Test
  void rejectsANegativeIdleSleep() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> properties(Duration.ofMillis(-1), 1024, Duration.ofSeconds(1)))
        .withMessageContaining("sender-idle-sleep");
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1})
  void rejectsATopOfBookIntervalThatIsNotPositive(long millis) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> properties(Duration.ofMillis(1), 1024, Duration.ofMillis(millis)))
        .withMessageContaining("best-bid-ask-interval");
  }

  @Test
  void conflatesTheTopOfBookEverySecond() throws Exception {
    assertThat(applicationYaml().getProperty("argus.feed-gateway.kafka.best-bid-ask-interval"))
        .isEqualTo("1s");
  }

  private static KafkaProperties properties(
      Duration senderIdleSleep, int bufferCapacity, Duration bestBidAskInterval) {
    return new KafkaProperties("t", "q", senderIdleSleep, bufferCapacity, bestBidAskInterval);
  }

  private static PropertySource<?> applicationYaml() throws Exception {
    return new YamlPropertySourceLoader()
        .load("application", new ClassPathResource("application.yml"))
        .getFirst();
  }
}
