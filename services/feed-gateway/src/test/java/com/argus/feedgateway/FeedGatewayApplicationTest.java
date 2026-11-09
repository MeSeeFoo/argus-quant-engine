package com.argus.feedgateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.argus.agent.AgentLifecycle;
import com.google.protobuf.Message;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    // Any free port, so the test never clashes with a gateway running locally
    properties = {
      "argus.feed-gateway.aeron.channel=aeron:udp?control=localhost:0|control-mode=dynamic|fc=max",
      // No Binance in tests: the connection fails and is logged, the gateway still starts
      "argus.feed-gateway.binance.uri=ws://localhost:1",
      "argus.feed-gateway.binance.api-key=test-key"
    })
class FeedGatewayApplicationTest {

  @Value("${local.server.port}")
  private int port;

  @Autowired private KafkaTemplate<String, Message> kafkaTemplate;
  // The adapter's, which replaces Spring Boot's LoggingProducerListener
  @Autowired private ProducerListener<Object, Object> producerListener;

  @Autowired private ApplicationContext context;

  /** So the sender starts before the gateway agent and stops after it, draining its last trades. */
  @Test
  void stopsTheKafkaSenderAfterTheGatewayAgent() {
    int sender = context.getBean("kafkaSenderAgent", AgentLifecycle.class).getPhase();
    int gateway = context.getBean("feedAgent", AgentLifecycle.class).getPhase();

    assertThat(sender).as("Spring stops the higher phases first").isLessThan(gateway);
  }

  @Test
  void reportsHealthUp() throws Exception {
    HttpResponse<String> response = get("/actuator/health");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\":\"UP\"");
  }

  @Test
  void exposesFastPathMetrics() throws Exception {
    assertThat(get("/actuator/metrics/argus.feed.messages.published").body())
        .contains("\"type\"", "trade", "best_bid_ask", "heartbeat");
    assertThat(get("/actuator/metrics/argus.feed.messages.dropped").body())
        .contains(
            "not_connected",
            "back_pressured",
            "other",
            "queue_full",
            "ring_full",
            "send_failed",
            "\"transport\"",
            "aeron",
            "kafka");
    assertThat(get("/actuator/metrics/argus.feed.messages.rejected").body())
        .contains(
            "unsupported", "unknown_symbol", "unrepresentable", "stale_book", "unknown_instrument");
    assertThat(get("/actuator/metrics/argus.feed.frames.dropped").statusCode()).isEqualTo(200);
  }

  @Test
  void countsWhatKafkaAcknowledgesWithTheAdaptersListener() {
    // KafkaTemplate has no getter for it
    assertThat(ReflectionTestUtils.getField(kafkaTemplate, "producerListener"))
        .as("Spring Boot plugs the adapter's listener into its template")
        .isSameAs(producerListener);
  }

  private HttpResponse<String> get(String path) throws Exception {
    try (HttpClient client = HttpClient.newHttpClient()) {
      return client.send(
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
          HttpResponse.BodyHandlers.ofString());
    }
  }
}
