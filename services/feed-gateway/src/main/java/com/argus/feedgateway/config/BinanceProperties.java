package com.argus.feedgateway.config;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binance's SBE market data stream.
 *
 * @param uri base address of the stream
 * @param apiKey read-only Ed25519 API key, from the {@code BINANCE_API_KEY} environment variable;
 *     never logged or committed
 * @param symbols the shard's instruments, as Binance names them ({@code BTCUSDT})
 */
@ConfigurationProperties("argus.feed-gateway.binance")
public record BinanceProperties(URI uri, String apiKey, List<String> symbols) {

  /** Never shows the API key. */
  @Override
  public String toString() {
    return "BinanceProperties[uri=" + uri + ", apiKey=***, symbols=" + symbols + "]";
  }
}
