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
  void copyingATopOfBookOverwritesEveryValue() {
    BestBidAsk book =
        new BestBidAsk()
            .instrument(BTCUSDT)
            .bookUpdateId(7)
            .bid(6421050, -2, 120, -5)
            .ask(6421051, -2, 3, -5)
            .exchangeTimestamp(21)
            .receiveTimestamp(22);
    BestBidAsk copy =
        new BestBidAsk()
            .instrument(new Instrument("BINANCE", "ETHUSDT"))
            .bookUpdateId(1)
            .bid(1, 0, 1, 0)
            .ask(1, 0, 1, 0)
            .exchangeTimestamp(1)
            .receiveTimestamp(1);

    assertThat(copy.copyFrom(book)).isSameAs(copy);

    assertThat(copy.instrument()).isSameAs(BTCUSDT);
    assertThat(copy.bookUpdateId()).isEqualTo(7);
    assertThat(copy.bidPrice()).isEqualTo(book.bidPrice());
    assertThat(copy.bidQuantity()).isEqualTo(book.bidQuantity());
    assertThat(copy.askPrice()).isEqualTo(book.askPrice());
    assertThat(copy.askQuantity()).isEqualTo(book.askQuantity());
    assertThat(copy.exchangeTimestamp()).isEqualTo(21);
    assertThat(copy.receiveTimestamp()).isEqualTo(22);
  }

  @Test
  void aCopiedTopOfBookDoesNotChangeWithTheOriginal() {
    BestBidAsk book = new BestBidAsk().instrument(BTCUSDT).bookUpdateId(7).bid(1, 0, 1, 0);

    BestBidAsk copy = new BestBidAsk().copyFrom(book);
    book.instrument(new Instrument("BINANCE", "ETHUSDT")).bookUpdateId(8).bid(2, 0, 2, 0);

    assertThat(copy.instrument()).isEqualTo(BTCUSDT);
    assertThat(copy.bookUpdateId()).isEqualTo(7);
    assertThat(copy.bidPrice()).isEqualTo(new BestBidAsk().bid(1, 0, 1, 0).bidPrice());
  }

  @Test
  void aCopyOfATopOfBookWithoutInstrumentHasNoneEither() {
    BestBidAsk copy = new BestBidAsk().instrument(BTCUSDT).copyFrom(new BestBidAsk());

    assertThatNullPointerException().isThrownBy(copy::instrument);
  }

  @Test
  void anEventWithoutInstrumentFailsFast() {
    assertThatNullPointerException().isThrownBy(() -> new Trade().instrument());
    assertThatNullPointerException().isThrownBy(() -> new BestBidAsk().instrument());
  }
}
