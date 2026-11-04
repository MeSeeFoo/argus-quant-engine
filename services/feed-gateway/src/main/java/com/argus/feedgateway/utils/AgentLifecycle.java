package com.argus.feedgateway.utils;

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
 *
 * <p>The methods are synchronized because Spring may call them from different threads: {@code
 * start} from the main thread, {@code stop} from the shutdown hook. The lock makes {@code runner}
 * visible across them and keeps two calls from starting or closing it twice. It is taken only at
 * startup and shutdown, never by the agent's thread. {@code stop} holds it while the agent closes,
 * so the agent must never call back into this class.
 */
public final class AgentLifecycle implements SmartLifecycle {

  private static final Logger LOG = LoggerFactory.getLogger(AgentLifecycle.class);

  private final Agent agent;
  private final IdleStrategy idleStrategy;
  private final int phase;

  private @Nullable AgentRunner runner;

  /** Runs {@code agent} in Spring's default phase: started last, stopped first. */
  public AgentLifecycle(Agent agent, IdleStrategy idleStrategy) {
    this(agent, idleStrategy, SmartLifecycle.DEFAULT_PHASE);
  }

  /**
   * Runs {@code agent} in {@code phase}. Spring starts the lower phases first and stops them last,
   * so an agent that others feed, such as the Kafka sender, takes a lower phase than they do.
   */
  public AgentLifecycle(Agent agent, IdleStrategy idleStrategy, int phase) {
    this.agent = agent;
    this.idleStrategy = idleStrategy;
    this.phase = phase;
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

  private void onError(Throwable error) {
    LOG.error("Agent {} failed", agent.roleName(), error);
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

  @Override
  public int getPhase() {
    return phase;
  }
}
