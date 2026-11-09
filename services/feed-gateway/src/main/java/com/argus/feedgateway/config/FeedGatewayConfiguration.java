package com.argus.feedgateway.config;

import com.argus.agent.AgentLifecycle;
import com.argus.feedgateway.application.FeedAgent;
import com.argus.feedgateway.application.MarketDataCounters;
import com.argus.feedgateway.application.MarketDataQueue;
import com.argus.feedgateway.application.MarketDataRelay;
import com.argus.feedgateway.application.port.out.MarketDataPublisher;
import com.argus.feedgateway.domain.Instrument;
import com.argus.feedgateway.domain.Instruments;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.List;
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

  /** Room for about fifteen thousand events between the inbound adapters and the gateway agent. */
  private static final int QUEUE_CAPACITY = 1 << 20;

  /**
   * The shard's instruments, in configuration order.
   *
   * @param properties the gateway's properties
   * @return the numbered instruments
   */
  @Bean
  Instruments instruments(FeedGatewayProperties properties) {
    return new Instruments(properties.instruments().stream().map(Instrument::parse).toList());
  }

  /**
   * The gateway's counters.
   *
   * @return a new set of counters
   */
  @Bean
  MarketDataCounters marketDataCounters() {
    return new MarketDataCounters();
  }

  /**
   * Relays to every outbound adapter that provides a {@code MarketDataPublisher}.
   *
   * @param properties the gateway's properties
   * @param instruments the shard's instruments
   * @param marketDataPublishers every outbound adapter
   * @param counters the gateway's counters
   * @return the relay
   */
  @Bean
  MarketDataRelay marketDataRelay(
      FeedGatewayProperties properties,
      Instruments instruments,
      List<MarketDataPublisher> marketDataPublishers,
      MarketDataCounters counters) {
    return new MarketDataRelay(
        instruments, marketDataPublishers, counters, properties.heartbeatInterval().toNanos());
  }

  /**
   * Where market data enters, offered to the inbound adapters as {@code IngestMarketData}.
   *
   * @param instruments the shard's instruments
   * @param marketDataRelay where drained events go
   * @param counters the gateway's counters
   * @return the queue
   */
  @Bean
  MarketDataQueue marketDataQueue(
      Instruments instruments, MarketDataRelay marketDataRelay, MarketDataCounters counters) {
    return new MarketDataQueue(instruments, marketDataRelay, counters, QUEUE_CAPACITY);
  }

  /**
   * The gateway agent, on its own thread from start to shut down.
   *
   * @param marketDataQueue what it drains
   * @param marketDataRelay where the events go
   * @return the agent's lifecycle, started by Spring
   */
  @Bean
  AgentLifecycle feedAgent(MarketDataQueue marketDataQueue, MarketDataRelay marketDataRelay) {
    return new AgentLifecycle(
        new FeedAgent(
            marketDataQueue, marketDataRelay, new SystemNanoClock(), new SystemEpochNanoClock()),
        new BackoffIdleStrategy());
  }

  /**
   * Publishes the gateway's counters as metrics.
   *
   * @param counters the gateway's counters
   * @return the binder that registers them
   */
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
      FunctionCounter.builder(
              "argus.feed.messages.rejected", counters, MarketDataCounters::unknownInstruments)
          .description("Venue messages received but not published")
          .tag("reason", "unknown_instrument")
          .register(registry);
      FunctionCounter.builder(
              "argus.feed.messages.dropped", counters, MarketDataCounters::droppedEvents)
          .description("Messages dropped instead of blocking")
          .tag("reason", "queue_full")
          // Dropped before reaching any transport; the tag keys must match the adapters' drops
          .tag("transport", "all")
          .register(registry);
    };
  }
}
