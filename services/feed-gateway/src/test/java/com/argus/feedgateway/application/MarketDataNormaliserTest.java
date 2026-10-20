package com.argus.feedgateway.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.Instrument;
import com.argus.feedgateway.domain.Instruments;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketDataNormaliserTest {

  private static final Instrument BTCUSDT = new Instrument("BINANCE", "BTCUSDT");
  private static final Instrument ETHUSDT = new Instrument("BINANCE", "ETHUSDT");

  private final RecordingPublisher publisher = new RecordingPublisher();
  private final MarketDataCounters counters = new MarketDataCounters();
  private final MarketDataNormaliser normaliser =
      new MarketDataNormaliser(new Instruments(List.of(BTCUSDT, ETHUSDT)), publisher, counters);

  @Test
  void publishesTradesInCanonicalValues() {
    normaliser.onTrade(1, 42, 345678, -2, 15, -3, AggressorSide.SELL, 1_000L, 2_000L);

    assertThat(publisher.trades)
        .containsExactly(
            new RecordingPublisher.Trade(
                ETHUSDT, 42, 345_678_000_000L, 1_500_000L, 1_000L, 2_000L, AggressorSide.SELL));
  }

  @Test
  void rejectsTradesWithUnrepresentableValues() {
    normaliser.onTrade(0, 1, 1, -9, 1, -2, AggressorSide.BUY, 1, 2);
    normaliser.onTrade(0, 2, 1, -2, 1, -9, AggressorSide.BUY, 1, 2);

    assertThat(publisher.trades).isEmpty();
    assertThat(counters.unrepresentableValues()).isEqualTo(2);
  }

  @Test
  void publishesTopOfBookInCanonicalValues() {
    normaliser.onBestBidAsk(0, 7, 6421050, 120, 6421051, 3, -2, -5, 1_000L, 2_000L);

    assertThat(publisher.bestBidAsks)
        .containsExactly(
            new RecordingPublisher.BestBidAsk(
                BTCUSDT,
                7,
                6_421_050_000_000L,
                120_000L,
                6_421_051_000_000L,
                3_000L,
                1_000L,
                2_000L));
  }

  @Test
  void dropsStaleTopOfBookUpdates() {
    normaliser.onBestBidAsk(0, 7, 1, 1, 2, 1, -2, -2, 1, 2);
    normaliser.onBestBidAsk(0, 7, 1, 1, 2, 1, -2, -2, 1, 2);
    normaliser.onBestBidAsk(0, 6, 1, 1, 2, 1, -2, -2, 1, 2);
    normaliser.onBestBidAsk(1, 6, 1, 1, 2, 1, -2, -2, 1, 2);

    assertThat(publisher.bestBidAsks)
        .extracting(RecordingPublisher.BestBidAsk::bookUpdateId)
        .containsExactly(7L, 6L);
    assertThat(counters.staleBookUpdates()).isEqualTo(2);
  }

  @Test
  void rejectsTopOfBookUpdatesWithAnyUnrepresentableValue() {
    long tooPrecise = 1;
    int exponent = -9;
    normaliser.onBestBidAsk(0, 1, tooPrecise, 0, 0, 0, exponent, 0, 1, 2);
    normaliser.onBestBidAsk(0, 2, 0, tooPrecise, 0, 0, 0, exponent, 1, 2);
    normaliser.onBestBidAsk(0, 3, 0, 0, tooPrecise, 0, exponent, 0, 1, 2);
    normaliser.onBestBidAsk(0, 4, 0, 0, 0, tooPrecise, 0, exponent, 1, 2);

    assertThat(publisher.bestBidAsks).isEmpty();
    assertThat(counters.unrepresentableValues()).isEqualTo(4);
  }
}
