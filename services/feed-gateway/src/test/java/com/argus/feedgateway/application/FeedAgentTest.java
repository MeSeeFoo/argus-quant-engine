package com.argus.feedgateway.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.argus.feedgateway.domain.Instrument;
import com.argus.feedgateway.domain.Instruments;
import com.argus.feedgateway.domain.Trade;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeedAgentTest {

  private static final Instrument BTCUSDT = new Instrument("BINANCE", "BTCUSDT");

  private final Instruments instruments = new Instruments(List.of(BTCUSDT));
  private final RecordingPublisher publisher = new RecordingPublisher();
  private final MarketDataCounters counters = new MarketDataCounters();
  private final MarketDataRelay relay = new MarketDataRelay(instruments, publisher, counters, 10);
  private final MarketDataQueue queue =
      new MarketDataQueue(instruments, relay, counters, 64 * 1024);
  private long monotonicNow;

  private final FeedAgent agent = new FeedAgent(queue, relay, () -> monotonicNow, () -> 22L);

  @Test
  void drainsTheQueueInBoundedBatchesAndKeepsTheStreamAlive() {
    Trade trade = new Trade().instrument(BTCUSDT).price(1, 0).quantity(1, 0);
    for (int i = 1; i <= FeedAgent.EVENT_LIMIT + 5; i++) {
      queue.onTrade(trade.tradeId(i));
    }

    assertThat(agent.doWork())
        .as("a batch: the trades published, so no heartbeat yet")
        .isEqualTo(FeedAgent.EVENT_LIMIT);
    assertThat(agent.doWork()).isEqualTo(5);
    monotonicNow = 10;
    assertThat(agent.doWork()).as("trades were published in the interval").isZero();
    monotonicNow = 20;
    assertThat(agent.doWork()).as("a whole interval idle: a heartbeat").isEqualTo(1);
    assertThat(agent.doWork()).as("idle").isZero();

    assertThat(publisher.trades).hasSize(FeedAgent.EVENT_LIMIT + 5);
    assertThat(publisher.heartbeats).as("stamped with the epoch clock").containsExactly(22L);
  }

  @Test
  void sendsAHeartbeatStraightAwayWhenIdle() {
    assertThat(agent.doWork()).isEqualTo(1);
    assertThat(publisher.heartbeats).containsExactly(22L);
  }

  @Test
  void isNamedAfterTheGateway() {
    assertThat(agent.roleName()).isEqualTo("feed-gateway");
  }
}
