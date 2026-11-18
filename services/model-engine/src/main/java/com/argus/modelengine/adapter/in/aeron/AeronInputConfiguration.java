package com.argus.modelengine.adapter.in.aeron;

import com.argus.agent.AgentLifecycle;
import com.argus.modelengine.application.port.in.IngestMarketData;
import com.argus.modelengine.domain.instrument.Instruments;
import io.aeron.Aeron;
import io.aeron.Subscription;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The inbound Aeron adapter: the subscription to the gateway's stream, the source that decodes it,
 * and the reader agent that polls it. It owns its properties ({@code argus.model-engine.aeron.*})
 * and only injects the shared Aeron client (ADR 0009). Spring stops the agent before it closes the
 * subscription.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AeronInputProperties.class)
class AeronInputConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(AeronInputConfiguration.class);

  /**
   * The subscription to the gateway's shard. Each gateway session that connects or leaves is
   * logged.
   *
   * @param aeron the Aeron client
   * @param properties the adapter's properties
   * @return the subscription
   */
  @Bean(destroyMethod = "close")
  Subscription marketDataSubscription(Aeron aeron, AeronInputProperties properties) {
    LOG.info(
        "Subscribing to the gateway's stream {} on {}",
        properties.streamId(),
        properties.channel());
    return aeron.addSubscription(
        properties.channel(),
        properties.streamId(),
        GatewaySessionLog::onAvailable,
        GatewaySessionLog::onUnavailable);
  }

  /**
   * Plain Java: Spring only creates it, and the reader agent calls it directly (ADR 0010).
   *
   * @param marketDataSubscription the gateway's stream
   * @param instruments the instruments the engine handles
   * @param ingest the reader's use case, which receives every message
   * @return the source
   */
  @Bean
  AeronMarketDataSource aeronMarketDataSource(
      Subscription marketDataSubscription, Instruments instruments, IngestMarketData ingest) {
    return new AeronMarketDataSource(marketDataSubscription, instruments, ingest);
  }

  /**
   * The reader agent, on its own thread from start to shut down. It backs off when idle instead of
   * spinning: threads cannot be pinned on Fargate (ADR 0021).
   *
   * @param source the source it polls
   * @return the agent's lifecycle, started by Spring
   */
  @Bean
  AgentLifecycle readerAgent(AeronMarketDataSource source) {
    return new AgentLifecycle(source.agent(), new BackoffIdleStrategy());
  }
}
