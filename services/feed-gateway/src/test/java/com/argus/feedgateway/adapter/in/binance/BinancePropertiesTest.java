package com.argus.feedgateway.adapter.in.binance;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class BinancePropertiesTest {

  @Test
  void neverShowsTheApiKey() {
    BinanceProperties properties =
        new BinanceProperties(URI.create("wss://example.test"), "secret-key");

    assertThat(properties.toString())
        .doesNotContain("secret-key")
        .contains("wss://example.test", "apiKey=***");
  }
}
