package com.argus.feedgateway.adapter.out.aeron;

import com.argus.feedgateway.application.port.out.MarketDataPublisher;
import io.aeron.Aeron;
import io.aeron.ExclusivePublication;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.function.ToDoubleFunction;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Aeron adapter: an embedded Media Driver, the Aeron client, the shard's publication (ADR 0021)
 * and the {@link MarketDataPublisher} on top of it, with its metrics. It owns its properties
 * ({@code argus.feed-gateway.aeron.*}); the rest of the gateway only sees the port. Beans are
 * closed in reverse order on shutdown: publication, client, driver.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AeronProperties.class)
class AeronConfiguration {

  /**
   * One driver per JVM, in a directory of its own that is deleted on start and on shutdown. Its
   * threads back off when idle instead of spinning: threads cannot be pinned on Fargate.
   */
  @Bean(destroyMethod = "close")
  MediaDriver mediaDriver() {
    return MediaDriver.launchEmbedded(
        new MediaDriver.Context()
            .threadingMode(ThreadingMode.SHARED)
            .sharedIdleStrategy(new BackoffIdleStrategy())
            .dirDeleteOnStart(true)
            .dirDeleteOnShutdown(true));
  }

  @Bean(destroyMethod = "close")
  Aeron aeron(MediaDriver mediaDriver) {
    return Aeron.connect(new Aeron.Context().aeronDirectoryName(mediaDriver.aeronDirectoryName()));
  }

  @Bean(destroyMethod = "close")
  ExclusivePublication marketDataPublication(Aeron aeron, AeronProperties properties) {
    return aeron.addExclusivePublication(properties.channel(), properties.streamId());
  }

  @Bean
  PublicationCounters publicationCounters() {
    return new PublicationCounters();
  }

  /** Plain Java: Spring only creates it. The gateway agent calls it directly (ADR 0010). */
  @Bean
  MarketDataPublisher marketDataPublisher(
      ExclusivePublication marketDataPublication, PublicationCounters publicationCounters) {
    return new AeronMarketDataPublisher(marketDataPublication, publicationCounters);
  }

  @Bean
  MeterBinder publicationMetrics(PublicationCounters counters) {
    return registry -> {
      published(registry, counters, "trade", PublicationCounters::trades);
      published(registry, counters, "best_bid_ask", PublicationCounters::bestBidAsks);
      published(registry, counters, "heartbeat", PublicationCounters::heartbeats);
      dropped(registry, counters, "not_connected", PublicationCounters::droppedNotConnected);
      dropped(registry, counters, "back_pressured", PublicationCounters::droppedBackPressured);
      dropped(registry, counters, "closed", PublicationCounters::droppedClosed);
      dropped(registry, counters, "other", PublicationCounters::droppedOther);
    };
  }

  private static void published(
      MeterRegistry registry,
      PublicationCounters counters,
      String type,
      ToDoubleFunction<PublicationCounters> count) {
    FunctionCounter.builder("argus.feed.messages.published", counters, count)
        .description("Messages published on the fast path")
        .tag("type", type)
        .register(registry);
  }

  private static void dropped(
      MeterRegistry registry,
      PublicationCounters counters,
      String reason,
      ToDoubleFunction<PublicationCounters> count) {
    FunctionCounter.builder("argus.feed.messages.dropped", counters, count)
        .description("Messages dropped instead of blocking the fast path")
        .tag("reason", reason)
        .register(registry);
  }
}
