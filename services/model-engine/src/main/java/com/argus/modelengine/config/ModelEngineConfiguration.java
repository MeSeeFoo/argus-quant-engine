package com.argus.modelengine.config;

import com.argus.agent.AgentLifecycle;
import com.argus.modelengine.application.ModelEngineAgent;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The engine itself, whatever its adapters: the agent that runs it. Spring builds the objects and
 * starts the agent, and is never on the path of a message (ADR 0010).
 */
@Configuration(proxyBeanMethods = false)
class ModelEngineConfiguration {

  /**
   * The engine's agent, on its own thread from start to shut down.
   *
   * @return the agent's lifecycle, started by Spring
   */
  @Bean
  AgentLifecycle modelEngineAgent() {
    return new AgentLifecycle(new ModelEngineAgent(), new BackoffIdleStrategy());
  }
}
