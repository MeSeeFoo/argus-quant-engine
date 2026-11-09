package com.argus.modelengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.argus.agent.AgentLifecycle;
import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

class ModelEngineApplicationTest {

  /** Started by hand, not with {@code @SpringBootTest}, so the test can also close it. */
  @Test
  void startsHealthyAndStopsCleanly() throws Exception {
    ConfigurableApplicationContext context =
        SpringApplication.run(ModelEngineApplication.class, "--server.port=0");
    AgentLifecycle agent = context.getBean("modelEngineAgent", AgentLifecycle.class);
    Aeron aeron = context.getBean(Aeron.class);
    Path driverDirectory = Path.of(context.getBean(MediaDriver.class).aeronDirectoryName());

    try {
      assertThat(agent.isRunning()).isTrue();
      assertThat(aeron.isClosed()).isFalse();
      assertThat(driverDirectory).exists();

      HttpResponse<String> health =
          get(
              context.getEnvironment().getRequiredProperty("local.server.port"),
              "/actuator/health");
      assertThat(health.statusCode()).isEqualTo(200);
      assertThat(health.body()).contains("\"status\":\"UP\"");
    } finally {
      context.close();
    }

    assertThat(agent.isRunning()).isFalse();
    assertThat(aeron.isClosed()).isTrue();
    assertThat(driverDirectory).doesNotExist();
  }

  private static HttpResponse<String> get(String port, String path) throws Exception {
    try (HttpClient client = HttpClient.newHttpClient()) {
      return client.send(
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
          HttpResponse.BodyHandlers.ofString());
    }
  }
}
