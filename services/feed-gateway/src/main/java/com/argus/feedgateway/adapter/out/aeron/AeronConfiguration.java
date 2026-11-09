package com.argus.feedgateway.adapter.out.aeron;

import com.argus.feedgateway.adapter.out.PublicationMetrics;
import com.argus.feedgateway.application.port.out.MarketDataPublisher;
import io.aeron.Aeron;
import io.aeron.ExclusivePublication;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Aeron adapter: an embedded Media Driver, the Aeron client, the shard's publication (ADR 0022)
 * and the {@link MarketDataPublisher} on top of it, with its metrics. It owns its properties
 * ({@code argus.feed-gateway.aeron.*}); the rest of the gateway only sees the port. Beans are
 * closed in reverse order on shutdown: publication, client, driver.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AeronProperties.class)
class AeronConfiguration {

  private static final String TRANSPORT = "aeron";

  /**
   * One driver per JVM, in a directory of its own that is deleted on start and on shutdown. Its
   * threads back off when idle instead of spinning: threads cannot be pinned on Fargate.
   *
   * @return the running driver
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

  /**
   * The Aeron client, connected to the embedded driver.
   *
   * @param mediaDriver the embedded driver
   * @return the connected client
   */
  @Bean(destroyMethod = "close")
  Aeron aeron(MediaDriver mediaDriver) {
    return Aeron.connect(new Aeron.Context().aeronDirectoryName(mediaDriver.aeronDirectoryName()));
  }

  /**
   * The shard's publication (ADR 0022). Exclusive: only one thread may offer to it.
   *
   * @param aeron the Aeron client
   * @param properties the adapter's properties
   * @return the publication
   */
  @Bean(destroyMethod = "close")
  ExclusivePublication marketDataPublication(Aeron aeron, AeronProperties properties) {
    return aeron.addExclusivePublication(properties.channel(), properties.streamId());
  }

  /**
   * Plain Java: Spring only creates it. The gateway agent calls it directly (ADR 0010). It owns its
   * counters, so its metrics never mix with another adapter's.
   *
   * @param marketDataPublication the shard's publication
   * @return the publisher
   */
  @Bean
  AeronMarketDataPublisher marketDataPublisher(ExclusivePublication marketDataPublication) {
    return new AeronMarketDataPublisher(marketDataPublication);
  }

  /**
   * Publishes the adapter's counters as metrics.
   *
   * @param publisher the publisher that owns the counters
   * @return the binder that registers them
   */
  @Bean
  MeterBinder aeronPublicationMetrics(AeronMarketDataPublisher publisher) {
    AeronCounters counters = publisher.aeronCounters();
    return registry -> {
      PublicationMetrics.register(registry, counters, TRANSPORT);
      PublicationMetrics.published(
          registry, counters, TRANSPORT, "heartbeat", AeronCounters::heartbeats);
      PublicationMetrics.dropped(
          registry, counters, TRANSPORT, "not_connected", AeronCounters::droppedNotConnected);
      PublicationMetrics.dropped(
          registry, counters, TRANSPORT, "back_pressured", AeronCounters::droppedBackPressured);
      PublicationMetrics.dropped(
          registry, counters, TRANSPORT, "closed", AeronCounters::droppedClosed);
      PublicationMetrics.dropped(
          registry, counters, TRANSPORT, "other", AeronCounters::droppedOther);
    };
  }
}
