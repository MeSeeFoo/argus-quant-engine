package com.argus.modelengine.application;

import org.agrona.concurrent.Agent;

/**
 * The engine's work on a single thread. Plain Java, run by an Agrona {@code AgentRunner} that
 * Spring only starts (ADR 0009, ADR 0010). It has no work yet: receiving market data arrives with
 * #111.
 */
public final class ModelEngineAgent implements Agent {

  @Override
  public int doWork() {
    return 0;
  }

  @Override
  public String roleName() {
    return "model-engine";
  }
}
