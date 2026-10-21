package com.argus.feedgateway.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.argus.feedgateway.application.port.in.MarketDataSource;
import com.argus.feedgateway.domain.Instrument;
import com.argus.feedgateway.domain.Instruments;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeedAgentTest {

  private final RecordingPublisher publisher = new RecordingPublisher();
  private int pendingFrames;
  private int requestedLimit;
  private long monotonicNow;

  private final MarketDataSource source =
      limit -> {
        requestedLimit = limit;
        int decoded = Math.min(limit, pendingFrames);
        pendingFrames -= decoded;
        return decoded;
      };

  private final MarketDataRelay relay =
      new MarketDataRelay(
          new Instruments(List.of(new Instrument("BINANCE", "BTCUSDT"))),
          publisher,
          new MarketDataCounters(),
          10);

  private final FeedAgent agent = new FeedAgent(source, relay, () -> monotonicNow, () -> 22L);

  @Test
  void drainsTheSourceInBoundedBatchesAndKeepsTheStreamAlive() {
    pendingFrames = FeedAgent.FRAME_LIMIT + 5;

    assertThat(agent.doWork())
        .as("a batch, and the first heartbeat")
        .isEqualTo(FeedAgent.FRAME_LIMIT + 1);
    assertThat(agent.doWork()).isEqualTo(5);
    monotonicNow = 10;
    assertThat(agent.doWork()).as("an interval later, idle: a heartbeat").isEqualTo(1);
    assertThat(agent.doWork()).as("idle").isZero();

    assertThat(requestedLimit).isEqualTo(FeedAgent.FRAME_LIMIT);
    assertThat(publisher.heartbeats).as("stamped with the epoch clock").containsExactly(22L, 22L);
  }

  @Test
  void isNamedAfterTheGateway() {
    assertThat(agent.roleName()).isEqualTo("feed-gateway");
  }
}
