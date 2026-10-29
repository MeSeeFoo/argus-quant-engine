package com.argus.feedgateway.adapter.in.binance;

import com.argus.feedgateway.application.port.in.IngestMarketData;
import com.argus.feedgateway.domain.Instruments;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.agrona.concurrent.SystemEpochNanoClock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Binance adapter: a connection to Binance's SBE stream for the gateway's instruments, which
 * hands the decoded events to the gateway through {@link IngestMarketData}. It owns its properties
 * ({@code argus.feed-gateway.binance.*}) and its metrics; the rest of the gateway only sees the
 * ports.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BinanceProperties.class)
class BinanceConfiguration {

  static final String VENUE = "BINANCE";

  /** A wide margin over the frames of these streams; a longer one is dropped and counted. */
  private static final int MAX_FRAME_LENGTH = 128 * 1024;

  @Bean
  BinanceCounters binanceCounters() {
    return new BinanceCounters();
  }

  /** Opened when the gateway starts and closed when it stops. */
  @Bean(initMethod = "connect", destroyMethod = "close")
  BinanceStreamClient binanceStreamClient(
      BinanceProperties properties,
      Instruments instruments,
      IngestMarketData ingestMarketData,
      BinanceCounters counters) {
    for (int i = 0; i < instruments.size(); i++) {
      if (!VENUE.equals(instruments.get(i).venue())) {
        throw new IllegalStateException(
            "The Binance adapter only reads " + VENUE + " instruments: " + instruments.get(i));
      }
    }
    return new BinanceStreamClient(
        BinanceStreamClient.streamUri(properties.uri(), instruments),
        properties.apiKey(),
        instruments,
        ingestMarketData,
        counters,
        new SystemEpochNanoClock(),
        MAX_FRAME_LENGTH);
  }

  @Bean
  MeterBinder binanceMetrics(BinanceCounters counters) {
    return registry -> {
      FunctionCounter.builder(
              "argus.feed.messages.rejected", counters, BinanceCounters::unsupportedMessages)
          .description("Venue messages received but not published")
          .tag("reason", "unsupported")
          .register(registry);
      FunctionCounter.builder(
              "argus.feed.messages.rejected", counters, BinanceCounters::unknownSymbols)
          .description("Venue messages received but not published")
          .tag("reason", "unknown_symbol")
          .register(registry);
      FunctionCounter.builder("argus.feed.frames.dropped", counters, BinanceCounters::droppedFrames)
          .description("Frames dropped on arrival because they were larger than accepted")
          .register(registry);
    };
  }
}
