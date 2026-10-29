package com.argus.feedgateway.adapter.in.binance;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binance's SBE market data stream. Which instruments to read is the gateway's own setting ({@code
 * argus.feed-gateway.instruments}).
 *
 * @param uri base address of the stream
 * @param apiKey read-only Ed25519 API key, from the {@code BINANCE_API_KEY} environment variable;
 *     never logged or committed
 */
@ConfigurationProperties("argus.feed-gateway.binance")
public record BinanceProperties(URI uri, String apiKey) {

  /** Never shows the API key. */
  @Override
  public String toString() {
    return "BinanceProperties[uri=" + uri + ", apiKey=***]";
  }
}
