package com.argus.modelengine.domain;

import static com.argus.modelengine.domain.TestEvents.SECOND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EventStoreTest {

  private final TestEvents events = new TestEvents();

  @Test
  void copiesEveryFieldOfATrade() {
    TradeStore store = new TradeStore(4, 4);
    EventWindow<Trade> window = store.slidingCount(1);

    store.append(events.trade(7, 50));

    Trade trade = window.get(0);
    assertThat(trade.instrumentId()).isEqualTo(1);
    assertThat(trade.sequence()).isEqualTo(107);
    assertThat(trade.tradeId()).isEqualTo(7);
    assertThat(trade.price()).isEqualTo(7);
    assertThat(trade.quantity()).isEqualTo(14);
    assertThat(trade.exchangeTimestamp()).isEqualTo(50);
    assertThat(trade.receiveTimestamp()).isEqualTo(51);
    assertThat(trade.aggressorSide()).isEqualTo(AggressorSide.SELL);
  }

  @Test
  void copiesEveryFieldOfATopOfBookUpdate() {
    BestBidAskStore store = new BestBidAskStore(4, 4);
    EventWindow<BestBidAsk> window = store.slidingCount(1);

    store.append(events.bestBidAsk(7, 50));

    BestBidAsk update = window.get(0);
    assertThat(update.instrumentId()).isEqualTo(2);
    assertThat(update.sequence()).isEqualTo(107);
    assertThat(update.bookUpdateId()).isEqualTo(7);
    assertThat(update.bidPrice()).isEqualTo(7);
    assertThat(update.bidQuantity()).isEqualTo(8);
    assertThat(update.askPrice()).isEqualTo(9);
    assertThat(update.askQuantity()).isEqualTo(10);
    assertThat(update.exchangeTimestamp()).isEqualTo(50);
    assertThat(update.receiveTimestamp()).isEqualTo(51);
  }

  @Test
  void reusesTheOldestObjectWhenNoWindowNeedsIt() {
    TradeStore store = new TradeStore(3, 8);
    EventWindow<Trade> window = store.slidingCount(2);
    store.append(events.trade(1, 1));
    Trade first = window.get(0);

    store.append(events.trade(2, 2));
    store.append(events.trade(3, 3));
    store.append(events.trade(4, 4));

    assertThat(store.capacity()).isEqualTo(3);
    assertThat(window.get(1)).isSameAs(first);
    assertThat(first.tradeId()).isEqualTo(4);
    assertThat(store.lostEvents()).isZero();
  }

  @Test
  void overwritesWithoutWindows() {
    TradeStore store = new TradeStore(2, 8);

    for (int i = 0; i < 10; i++) {
      store.append(events.trade(i, i));
    }

    assertThat(store.capacity()).isEqualTo(2);
    assertThat(store.lostEvents()).isZero();
  }

  @Test
  void growsWhileAWindowNeedsTheOldestEvent() {
    TradeStore store = new TradeStore(2, 8);
    EventWindow<Trade> window = store.slidingTime(10 * SECOND);

    for (int i = 0; i < 5; i++) {
      store.append(events.trade(i, i * SECOND));
    }

    assertThat(store.capacity()).isEqualTo(8);
    assertThat(window.size()).isEqualTo(5);
    for (int i = 0; i < 5; i++) {
      assertThat(window.get(i).tradeId()).isEqualTo(i);
    }
  }

  @Test
  void growsOnlyUpToItsMaximum() {
    TradeStore store = new TradeStore(2, 3);
    store.slidingTime(10 * SECOND);

    for (int i = 0; i < 5; i++) {
      store.append(events.trade(i, i * SECOND));
    }

    assertThat(store.capacity()).isEqualTo(3);
    assertThat(store.lostEvents()).isEqualTo(2);
  }

  @Test
  void stopsGrowingOnceTheWindowsFit() {
    TradeStore store = new TradeStore(2, 1024);
    EventWindow<Trade> window = store.slidingTime(10 * SECOND);

    // One event per second: the window holds ten, so the store settles at the next doubling
    for (int i = 0; i < 1_000; i++) {
      store.append(events.trade(i, i * SECOND));
    }

    assertThat(store.capacity()).isEqualTo(16);
    assertThat(window.size()).isEqualTo(10);
    assertThat(store.lostEvents()).isZero();
  }

  @Test
  void rejectsInvalidSizes() {
    assertThatThrownBy(() -> new TradeStore(0, 4)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new BestBidAskStore(4, 3))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsACountWindowThatCouldNeverFit() {
    TradeStore store = new TradeStore(2, 4);

    assertThatThrownBy(() -> store.slidingCount(5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maximum");
    assertThatThrownBy(() -> store.tumblingCount(3)).isInstanceOf(IllegalArgumentException.class);
  }
}
