package com.argus.feedgateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AgentLifecycleTest {

  private final RecordingAgent agent = new RecordingAgent();
  private final AgentLifecycle lifecycle =
      new AgentLifecycle(agent, new SleepingMillisIdleStrategy(1));

  @AfterEach
  void stopAgent() {
    lifecycle.stop();
  }

  @Test
  void runsTheAgentOnItsOwnThreadUntilStopped() throws InterruptedException {
    lifecycle.start();

    assertThat(lifecycle.isRunning()).isTrue();
    assertThat(agent.worked.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(agent.threadName).isEqualTo("test-agent");

    lifecycle.stop();

    assertThat(lifecycle.isRunning()).isFalse();
    assertThat(agent.starts).hasValue(1);
    assertThat(agent.closes).hasValue(1);
  }

  @Test
  void startingTwiceRunsOneAgent() throws InterruptedException {
    lifecycle.start();
    lifecycle.start();
    assertThat(agent.worked.await(5, TimeUnit.SECONDS)).isTrue();

    lifecycle.stop();

    assertThat(agent.starts).hasValue(1);
  }

  @Test
  void stoppingWhenNotRunningDoesNothing() {
    lifecycle.stop();

    assertThat(lifecycle.isRunning()).isFalse();
    assertThat(agent.closes).hasValue(0);
  }

  @Test
  void keepsRunningAfterTheAgentThrows() throws InterruptedException {
    agent.failFirstWork = true;

    lifecycle.start();

    assertThat(agent.worked.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(lifecycle.isRunning()).isTrue();
  }

  private static final class RecordingAgent implements Agent {

    final CountDownLatch worked = new CountDownLatch(3);
    final AtomicInteger starts = new AtomicInteger();
    final AtomicInteger closes = new AtomicInteger();
    volatile boolean failFirstWork;
    volatile String threadName = "";

    @Override
    public void onStart() {
      starts.incrementAndGet();
      threadName = Thread.currentThread().getName();
    }

    @Override
    public int doWork() {
      if (failFirstWork) {
        failFirstWork = false;
        throw new IllegalStateException("deliberate failure");
      }
      worked.countDown();
      return 0;
    }

    @Override
    public void onClose() {
      closes.incrementAndGet();
    }

    @Override
    public String roleName() {
      return "test-agent";
    }
  }
}
