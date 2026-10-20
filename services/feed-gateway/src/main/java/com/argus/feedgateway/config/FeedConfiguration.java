package com.argus.feedgateway.config;

import com.argus.feedgateway.adapter.in.binance.BinanceStreamClient;
import com.argus.feedgateway.adapter.in.binance.BinanceStreamDecoder;
import com.argus.feedgateway.adapter.out.aeron.AeronMarketDataPublisher;
import com.argus.feedgateway.application.FeedAgent;
import com.argus.feedgateway.application.MarketDataCounters;
import com.argus.feedgateway.application.MarketDataNormaliser;
import com.argus.feedgateway.domain.Instrument;
import com.argus.feedgateway.domain.Instruments;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.function.ToDoubleFunction;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.SystemEpochNanoClock;
import org.agrona.concurrent.SystemNanoClock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The feed: Binance's stream, decoded and normalised by the gateway agent on its own thread, and
 * published on the fast path. Spring builds the objects and starts the agent; it is never on the
 * path of a message (ADR 0010).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BinanceProperties.class)
class FeedConfiguration {

  private static final String VENUE = "BINANCE";

  /** About ten thousand frames between the connection and the agent. */
  private static final int RING_CAPACITY = 1 << 20;

  @Bean
  Instruments instruments(BinanceProperties properties) {
    return new Instruments(
        properties.symbols().stream().map(symbol -> new Instrument(VENUE, symbol)).toList());
  }

  @Bean
  MarketDataCounters marketDataCounters() {
    return new MarketDataCounters();
  }

  @Bean(initMethod = "connect", destroyMethod = "close")
  BinanceStreamClient binanceStreamClient(
      BinanceProperties properties,
      Instruments instruments,
      AeronMarketDataPublisher marketDataPublisher,
      MarketDataCounters counters) {
    BinanceStreamDecoder decoder =
        new BinanceStreamDecoder(
            instruments,
            new MarketDataNormaliser(instruments, marketDataPublisher, counters),
            counters);
    return new BinanceStreamClient(
        BinanceStreamClient.streamUri(properties.uri(), instruments),
        properties.apiKey(),
        decoder,
        counters,
        new SystemEpochNanoClock(),
        RING_CAPACITY);
  }

  /** The gateway agent, on its own thread from start to shutdown. */
  @Bean
  AgentLifecycle feedAgent(
      BinanceStreamClient binanceStreamClient, AeronMarketDataPublisher marketDataPublisher) {
    return new AgentLifecycle(
        new FeedAgent(
            binanceStreamClient,
            marketDataPublisher,
            new SystemNanoClock(),
            new SystemEpochNanoClock()),
        new BackoffIdleStrategy());
  }

  @Bean
  MeterBinder feedMetrics(MarketDataCounters counters) {
    return registry -> {
      rejected(registry, counters, "unsupported", MarketDataCounters::unsupportedMessages);
      rejected(registry, counters, "unknown_symbol", MarketDataCounters::unknownSymbols);
      rejected(registry, counters, "unrepresentable", MarketDataCounters::unrepresentableValues);
      rejected(registry, counters, "stale_book", MarketDataCounters::staleBookUpdates);
      FunctionCounter.builder(
              "argus.feed.frames.dropped", counters, MarketDataCounters::droppedFrames)
          .description("Frames dropped on arrival because the gateway agent was behind")
          .register(registry);
    };
  }

  private static void rejected(
      MeterRegistry registry,
      MarketDataCounters counters,
      String reason,
      ToDoubleFunction<MarketDataCounters> count) {
    FunctionCounter.builder("argus.feed.messages.rejected", counters, count)
        .description("Venue messages received but not published")
        .tag("reason", reason)
        .register(registry);
  }
}
