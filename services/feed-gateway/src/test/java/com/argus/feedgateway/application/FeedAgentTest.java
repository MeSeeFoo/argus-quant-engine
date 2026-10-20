package com.argus.feedgateway.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FeedAgentTest {

  private final RecordingPublisher publisher = new RecordingPublisher();
  private int pendingFrames;
  private int requestedLimit;

  private final MarketDataSource source =
      limit -> {
        requestedLimit = limit;
        int decoded = Math.min(limit, pendingFrames);
        pendingFrames -= decoded;
        return decoded;
      };

  private final FeedAgent agent = new FeedAgent(source, publisher, () -> 11L, () -> 22L);

  @Test
  void drainsTheSourceInBoundedBatchesAndChecksTheHeartbeatEveryCycle() {
    pendingFrames = FeedAgent.FRAME_LIMIT + 5;

    assertThat(agent.doWork()).isEqualTo(FeedAgent.FRAME_LIMIT);
    assertThat(agent.doWork()).isEqualTo(5);
    assertThat(agent.doWork()).as("idle").isZero();

    assertThat(requestedLimit).isEqualTo(FeedAgent.FRAME_LIMIT);
    assertThat(publisher.heartbeatChecks)
        .hasSize(3)
        .allSatisfy(clocks -> assertThat(clocks).containsExactly(11L, 22L));
  }

  @Test
  void isNamedAfterTheGateway() {
    assertThat(agent.roleName()).isEqualTo("feed-gateway");
  }
}
