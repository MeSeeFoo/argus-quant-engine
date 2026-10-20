package com.argus.feedgateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    // Any free port, so the test never clashes with a gateway running locally
    properties =
        "argus.feed-gateway.aeron.channel=aeron:udp?control=localhost:0|control-mode=dynamic|fc=max")
class FeedGatewayApplicationTest {

  @Value("${local.server.port}")
  private int port;

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
        .contains("not_connected", "back_pressured", "other");
  }

  private HttpResponse<String> get(String path) throws Exception {
    try (HttpClient client = HttpClient.newHttpClient()) {
      return client.send(
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
          HttpResponse.BodyHandlers.ofString());
    }
  }
}
