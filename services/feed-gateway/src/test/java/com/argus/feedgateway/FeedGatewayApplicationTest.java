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

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class FeedGatewayApplicationTest {

  @Value("${local.server.port}")
  private int port;

  @Test
  void reportsHealthUp() throws Exception {
    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpResponse<String> response =
          client.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).contains("\"status\":\"UP\"");
    }
  }
}
