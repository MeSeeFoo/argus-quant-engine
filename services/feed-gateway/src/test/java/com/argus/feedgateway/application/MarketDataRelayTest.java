package com.argus.feedgateway.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.BestBidAsk;
import com.argus.feedgateway.domain.Instrument;
import com.argus.feedgateway.domain.Instruments;
import com.argus.feedgateway.domain.Trade;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketDataRelayTest {

  private static final Instrument BTCUSDT = new Instrument("BINANCE", "BTCUSDT");
  private static final Instrument ETHUSDT = new Instrument("BINANCE", "ETHUSDT");
  private static final long INTERVAL = 1_000_000_000L;

  private final RecordingPublisher publisher = new RecordingPublisher();
  private final MarketDataCounters counters = new MarketDataCounters();
  private final MarketDataRelay relay =
      new MarketDataRelay(
          new Instruments(List.of(BTCUSDT, ETHUSDT)), publisher, counters, INTERVAL);
  private final Trade trade = new Trade();
  private final BestBidAsk book = new BestBidAsk();

  @Test
  void publishesTrades() {
    relay.onTrade(
        trade
            .instrument(ETHUSDT)
            .tradeId(42)
            .price(345678, -2)
            .quantity(15, -3)
            .aggressorSide(AggressorSide.SELL)
            .exchangeTimestamp(1_000)
            .receiveTimestamp(2_000));

    assertThat(publisher.trades)
        .containsExactly(
            new RecordingPublisher.Trade(
                ETHUSDT, 42, 345_678_000_000L, 1_500_000L, 1_000, 2_000, AggressorSide.SELL));
  }

  @Test
  void publishesTopOfBook() {
    relay.onBestBidAsk(
        book.instrument(BTCUSDT)
            .bookUpdateId(7)
            .bid(6421050, -2, 120, -5)
            .ask(6421051, -2, 3, -5)
            .exchangeTimestamp(1_000)
            .receiveTimestamp(2_000));

    assertThat(publisher.bestBidAsks)
        .containsExactly(
            new RecordingPublisher.BestBidAsk(
                BTCUSDT,
                7,
                6_421_050_000_000L,
                120_000L,
                6_421_051_000_000L,
                3_000L,
                1_000,
                2_000));
  }

  @Test
  void dropsStaleTopOfBookUpdates() {
    relay.onBestBidAsk(update(BTCUSDT, 7));
    relay.onBestBidAsk(update(BTCUSDT, 7));
    relay.onBestBidAsk(update(BTCUSDT, 6));
    relay.onBestBidAsk(update(ETHUSDT, 6));

    assertThat(publisher.bestBidAsks)
        .extracting(RecordingPublisher.BestBidAsk::bookUpdateId)
        .containsExactly(7L, 6L);
    assertThat(counters.staleBookUpdates()).isEqualTo(2);
  }

  @Test
  void asksForHeartbeatsOnlyWhileIdle() {
    // A monotonic clock that is negative, as nanoTime may be, and an unrelated epoch clock
    long start = -5 * INTERVAL;
    long epoch = 1_760_000_000_000_000_000L;

    assertThat(relay.heartbeatIfIdle(start, epoch)).as("idle since start").isEqualTo(1);
    assertThat(relay.heartbeatIfIdle(start + INTERVAL - 1, epoch + 1)).isZero();
    relay.onTrade(trade.instrument(BTCUSDT).tradeId(1).price(1, 0).quantity(1, 0));
    assertThat(relay.heartbeatIfIdle(start + INTERVAL, epoch + 2))
        .as("a trade was published in the interval")
        .isZero();
    assertThat(relay.heartbeatIfIdle(start + 2 * INTERVAL, epoch + 3)).isEqualTo(1);
    assertThat(relay.heartbeatIfIdle(start + 3 * INTERVAL, epoch + 4)).isEqualTo(1);

    assertThat(publisher.heartbeats)
        .as("stamped with the epoch clock")
        .containsExactly(epoch, epoch + 3, epoch + 4);
  }

  @Test
  void aStaleUpdateDoesNotCountAsActivity() {
    relay.onBestBidAsk(update(BTCUSDT, 5));
    assertThat(relay.heartbeatIfIdle(0, 0)).as("an update was published").isZero();
    relay.onBestBidAsk(update(BTCUSDT, 5));

    assertThat(relay.heartbeatIfIdle(INTERVAL, 1)).isEqualTo(1);
  }

  @Test
  void requiresAPositiveHeartbeatInterval() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new MarketDataRelay(new Instruments(List.of(BTCUSDT)), publisher, counters, 0));
  }

  private BestBidAsk update(Instrument instrument, long bookUpdateId) {
    return book.instrument(instrument).bookUpdateId(bookUpdateId).bid(1, 0, 1, 0).ask(2, 0, 1, 0);
  }
}
