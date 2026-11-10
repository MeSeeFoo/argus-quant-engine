package com.argus.modelengine.domain.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.argus.modelengine.domain.marketdata.AggressorSide;
import com.argus.modelengine.domain.marketdata.BestBidAsk;
import com.argus.modelengine.domain.marketdata.Trade;
import org.junit.jupiter.api.Test;

class EventStoreTest {

  private final TestEvents events = new TestEvents();

  @Test
  void copiesEveryFieldOfATrade() {
    TradeStore store = new TradeStore(4);
    EventWindow<Trade> window = store.window(WindowDefinition.sliding(1));

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
    BestBidAskStore store = new BestBidAskStore(4);
    EventWindow<BestBidAsk> window = store.window(WindowDefinition.sliding(1));

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
  void reusesTheOldestObjectOnceFull() {
    TradeStore store = new TradeStore(3);
    EventWindow<Trade> window = store.window(WindowDefinition.sliding(2));
    store.append(events.trade(1, 1));
    Trade first = window.get(0);

    store.append(events.trade(2, 2));
    store.append(events.trade(3, 3));
    store.append(events.trade(4, 4));

    assertThat(store.capacity()).isEqualTo(3);
    assertThat(window.get(1)).isSameAs(first);
    assertThat(first.tradeId()).isEqualTo(4);
  }

  @Test
  void keepsItsCapacityWithoutWindows() {
    TradeStore store = new TradeStore(2);

    for (int i = 0; i < 10; i++) {
      store.append(events.trade(i, i));
    }

    assertThat(store.capacity()).isEqualTo(2);
  }

  @Test
  void rejectsAnInvalidCapacity() {
    assertThatThrownBy(() -> new TradeStore(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new BestBidAskStore(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAWindowThatDoesNotFit() {
    TradeStore store = new TradeStore(4);

    assertThatThrownBy(() -> store.window(WindowDefinition.sliding(5)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("needs 5 events but the store keeps 4");
    assertThatThrownBy(() -> store.window(WindowDefinition.tumbling(3)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsWindowsThatFitExactly() {
    TradeStore store = new TradeStore(5);

    store.window(WindowDefinition.sliding(5));
    store.window(WindowDefinition.tumbling(3));

    assertThat(store.capacity()).isEqualTo(5);
  }
}
