package com.argus.feedgateway.config;

import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.IdleStrategy;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Runs one Agrona agent on its own thread, named after the agent's role, while the Spring context
 * is running. The agent stays plain Java: Spring starts and stops it, nothing more.
 */
final class AgentLifecycle implements SmartLifecycle {

  private static final Logger LOG = LoggerFactory.getLogger(AgentLifecycle.class);

  private final Agent agent;
  private final IdleStrategy idleStrategy;
  private @Nullable AgentRunner runner;

  AgentLifecycle(Agent agent, IdleStrategy idleStrategy) {
    this.agent = agent;
    this.idleStrategy = idleStrategy;
  }

  @Override
  public synchronized void start() {
    if (runner != null) {
      return;
    }
    AgentRunner newRunner = new AgentRunner(idleStrategy, this::onError, null, agent);
    AgentRunner.startOnThread(newRunner);
    runner = newRunner;
  }

  /** Stops the agent and waits for its thread to run {@code onClose}. */
  @Override
  public synchronized void stop() {
    if (runner == null) {
      return;
    }
    runner.close();
    runner = null;
  }

  @Override
  public synchronized boolean isRunning() {
    return runner != null;
  }

  private void onError(Throwable error) {
    LOG.error("Agent {} failed", agent.roleName(), error);
  }
}
