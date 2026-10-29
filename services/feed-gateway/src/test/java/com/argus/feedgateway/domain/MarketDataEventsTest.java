package com.argus.feedgateway.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class MarketDataEventsTest {

  private static final Instrument BTCUSDT = new Instrument("BINANCE", "BTCUSDT");

  @Test
  void aTradeConvertsTheVenueDecimals() {
    Trade trade = new Trade().instrument(BTCUSDT).price(6421050, -2).quantity(15, -3);

    assertThat(trade.price()).isEqualTo(6_421_050_000_000L);
    assertThat(trade.quantity()).isEqualTo(1_500_000L);
    assertThat(trade.isRepresentable()).isTrue();
    assertThat(trade.instrument()).isSameAs(BTCUSDT);
  }

  @Test
  void aTradeWithTooManyDecimalsIsNotRepresentable() {
    assertThat(new Trade().price(1, -9).quantity(1, 0).isRepresentable()).isFalse();
    assertThat(new Trade().price(1, 0).quantity(1, -9).isRepresentable()).isFalse();
  }

  @Test
  void aTopOfBookConvertsTheVenueDecimals() {
    BestBidAsk book = new BestBidAsk().bid(6421050, -2, 120, -5).ask(6421051, -2, 3, -5);

    assertThat(book.bidPrice()).isEqualTo(6_421_050_000_000L);
    assertThat(book.bidQuantity()).isEqualTo(120_000L);
    assertThat(book.askPrice()).isEqualTo(6_421_051_000_000L);
    assertThat(book.askQuantity()).isEqualTo(3_000L);
    assertThat(book.isRepresentable()).isTrue();
  }

  @Test
  void aTopOfBookWithAnyValueTooPreciseIsNotRepresentable() {
    assertThat(new BestBidAsk().bid(1, -9, 1, 0).ask(1, 0, 1, 0).isRepresentable()).isFalse();
    assertThat(new BestBidAsk().bid(1, 0, 1, -9).ask(1, 0, 1, 0).isRepresentable()).isFalse();
    assertThat(new BestBidAsk().bid(1, 0, 1, 0).ask(1, -9, 1, 0).isRepresentable()).isFalse();
    assertThat(new BestBidAsk().bid(1, 0, 1, 0).ask(1, 0, 1, -9).isRepresentable()).isFalse();
  }

  @Test
  void anEventWithoutInstrumentFailsFast() {
    assertThatNullPointerException().isThrownBy(() -> new Trade().instrument());
    assertThatNullPointerException().isThrownBy(() -> new BestBidAsk().instrument());
  }
}
