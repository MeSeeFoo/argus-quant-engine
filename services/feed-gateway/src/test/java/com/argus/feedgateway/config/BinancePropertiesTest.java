package com.argus.feedgateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class BinancePropertiesTest {

  @Test
  void neverShowsTheApiKey() {
    BinanceProperties properties =
        new BinanceProperties(URI.create("wss://example.test"), "secret-key", List.of("BTCUSDT"));

    assertThat(properties.toString())
        .doesNotContain("secret-key")
        .contains("wss://example.test", "BTCUSDT", "apiKey=***");
  }
}
