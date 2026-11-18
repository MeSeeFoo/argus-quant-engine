package com.argus.modelengine.config;

import com.argus.agent.AgentLifecycle;
import com.argus.modelengine.application.Evaluator;
import com.argus.modelengine.application.MarketDataCounters;
import com.argus.modelengine.application.MarketDataEventChecker;
import com.argus.modelengine.application.MarketDataService;
import com.argus.modelengine.domain.feed.Deduplicator;
import com.argus.modelengine.domain.feed.SequenceGapDetector;
import com.argus.modelengine.domain.instrument.Instrument;
import com.argus.modelengine.domain.instrument.Instruments;
import java.util.List;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The engine itself, whatever its adapters: the instruments, the reader's use case, the ring and
 * the evaluator agent. Spring builds the objects and starts the agent, and is never on the path of
 * a message (ADR 0010).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ModelEngineProperties.class)
class ModelEngineConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(ModelEngineConfiguration.class);

  /**
   * The engine's instruments, in configuration order.
   *
   * @param properties the engine's properties
   * @return the numbered instruments
   */
  @Bean
  Instruments instruments(ModelEngineProperties properties) {
    LOG.info(
        "Handling {} instruments: {}", properties.instruments().size(), properties.instruments());
    return new Instruments(properties.instruments().stream().map(Instrument::parse).toList());
  }

  /**
   * The reader's checks: duplicate and old events, and gaps in {@code sequence}.
   *
   * @param instruments the engine's instruments
   * @param counters where what the checks find is counted
   * @return the checks
   */
  @Bean
  MarketDataEventChecker marketDataEventChecker(
      Instruments instruments, MarketDataCounters counters) {
    return new MarketDataEventChecker(
        new Deduplicator(instruments.size()),
        new Deduplicator(instruments.size()),
        new SequenceGapDetector(),
        counters);
  }

  /**
   * Everything the engine's application counts, written by the reader and the evaluator, and read
   * by the metrics.
   *
   * @return the counters
   */
  @Bean
  MarketDataCounters marketDataCounters() {
    return new MarketDataCounters();
  }

  /**
   * The reader's use case and the Disruptor ring it publishes to.
   *
   * @param properties the engine's properties
   * @param checker the reader's checks
   * @param counters where what the ring loses is counted
   * @return the service
   */
  @Bean
  MarketDataService marketDataService(
      ModelEngineProperties properties,
      MarketDataEventChecker checker,
      MarketDataCounters counters) {
    return new MarketDataService(
        checker, properties.ringSize(), properties.backpressure(), counters);
  }

  /**
   * The evaluator agent, on its own thread from start to shut down. It runs no deployment yet: they
   * arrive from configuration with #112.
   *
   * @param instruments the engine's instruments
   * @param marketDataService the ring to drain
   * @param counters where failed evaluations are counted
   * @return the agent's lifecycle, started by Spring
   */
  @Bean
  AgentLifecycle evaluatorAgent(
      Instruments instruments, MarketDataService marketDataService, MarketDataCounters counters) {
    return new AgentLifecycle(
        new Evaluator(
            marketDataService.newPoller(),
            marketDataService.ringOverflowMechanism(),
            counters,
            instruments.size(),
            List.of()),
        new BackoffIdleStrategy());
  }
}
