package com.argus.feedgateway.config;

import com.argus.feedgateway.application.FeedAgent;
import com.argus.feedgateway.application.MarketDataCounters;
import com.argus.feedgateway.application.MarketDataRelay;
import com.argus.feedgateway.application.port.out.MarketDataPublisher;
import com.argus.feedgateway.application.port.out.MarketDataSource;
import com.argus.feedgateway.domain.Instrument;
import com.argus.feedgateway.domain.Instruments;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.SystemEpochNanoClock;
import org.agrona.concurrent.SystemNanoClock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The gateway itself, whatever its adapters: the instruments, the use case and the agent that runs
 * it. It only sees the adapters through the ports they provide (ADR 0009); Spring builds the
 * objects and starts the agent, and is never on the path of a message (ADR 0010).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FeedGatewayProperties.class)
class FeedGatewayConfiguration {

  @Bean
  Instruments instruments(FeedGatewayProperties properties) {
    return new Instruments(properties.instruments().stream().map(Instrument::parse).toList());
  }

  @Bean
  MarketDataCounters marketDataCounters() {
    return new MarketDataCounters();
  }

  /** The use case, offered to the inbound adapters as {@code IngestMarketData}. */
  @Bean
  MarketDataRelay marketDataRelay(
      FeedGatewayProperties properties,
      Instruments instruments,
      MarketDataPublisher marketDataPublisher,
      MarketDataCounters counters) {
    return new MarketDataRelay(
        instruments, marketDataPublisher, counters, properties.heartbeatInterval().toNanos());
  }

  /** The gateway agent, on its own thread from start to shutdown. */
  @Bean
  AgentLifecycle feedAgent(MarketDataSource marketDataSource, MarketDataRelay marketDataRelay) {
    return new AgentLifecycle(
        new FeedAgent(
            marketDataSource, marketDataRelay, new SystemNanoClock(), new SystemEpochNanoClock()),
        new BackoffIdleStrategy());
  }

  @Bean
  MeterBinder marketDataMetrics(MarketDataCounters counters) {
    return registry -> {
      FunctionCounter.builder(
              "argus.feed.messages.rejected", counters, MarketDataCounters::unrepresentableValues)
          .description("Venue messages received but not published")
          .tag("reason", "unrepresentable")
          .register(registry);
      FunctionCounter.builder(
              "argus.feed.messages.rejected", counters, MarketDataCounters::staleBookUpdates)
          .description("Venue messages received but not published")
          .tag("reason", "stale_book")
          .register(registry);
    };
  }
}
