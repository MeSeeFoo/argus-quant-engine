package com.argus.modelengine.domain.window;

import static com.argus.modelengine.domain.window.TestEvents.SECOND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.argus.modelengine.domain.marketdata.AggressorSide;
import com.argus.modelengine.domain.marketdata.BestBidAsk;
import com.argus.modelengine.domain.marketdata.MarketEventType;
import com.argus.modelengine.domain.marketdata.Trade;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventWindowTest {

  private final TestEvents events = new TestEvents();

  @Test
  void theViewDescribesTheInputItServes() {
    Window<Trade> trades = EventWindow.ofTrades("trades", WindowDefinition.sliding(3)).view();
    Window<BestBidAsk> quotes =
        EventWindow.ofBestBidAsks("quotes", WindowDefinition.tumbling(100)).view();

    assertThat(trades.name()).isEqualTo("trades");
    assertThat(trades.eventType()).isEqualTo(MarketEventType.TRADE);
    assertThat(trades.windowType()).isEqualTo(WindowDefinition.Type.SLIDING);
    assertThat(quotes.name()).isEqualTo("quotes");
    assertThat(quotes.eventType()).isEqualTo(MarketEventType.BEST_BID_ASK);
    assertThat(quotes.windowType()).isEqualTo(WindowDefinition.Type.TUMBLING);
  }

  @Test
  void aSlidingWindowHoldsTheLastEventsAndFiresOnEachOneOnceFull() {
    EventWindow<Trade> window = EventWindow.ofTrades("trades", WindowDefinition.sliding(3));

    assertThat(window.append(events.trade(1, 1))).isNull();
    assertThat(window.append(events.trade(2, 2))).isNull();
    for (int id = 3; id <= 5; id++) {
      assertThat(window.append(events.trade(id, id))).isSameAs(window.view());
    }

    assertThat(ids(window.view())).containsExactly(3L, 4L, 5L);
  }

  @Test
  void aSlidingWindowWithASlideFiresOnceFullThenEverySlideEvents() {
    EventWindow<Trade> window = EventWindow.ofTrades("trades", WindowDefinition.sliding(4, 2));

    assertThat(firedAt(window, 9)).containsExactly(4, 6, 8);
    assertThat(ids(window.view())).containsExactly(6L, 7L, 8L, 9L);
  }

  @Test
  void aSlideOfTheWholeCountFiresOncePerCount() {
    EventWindow<Trade> window = EventWindow.ofTrades("trades", WindowDefinition.sliding(3, 3));

    assertThat(firedAt(window, 9)).containsExactly(3, 6, 9);
    assertThat(ids(window.view())).containsExactly(7L, 8L, 9L);
  }

  @Test
  void betweenFiringsTheViewHoldsTheLastEventsInOrder() {
    // A model with several inputs reads this one when another fires: never a mix of old and new
    EventWindow<Trade> window = EventWindow.ofTrades("trades", WindowDefinition.sliding(3, 3));

    assertThat(firedAt(window, 4)).containsExactly(3);
    assertThat(ids(window.view())).containsExactly(2L, 3L, 4L);
  }

  @Test
  void aSlideIsBetweenOneAndTheCount() {
    assertThat(WindowDefinition.sliding(3).slide()).as("every event by default").isEqualTo(1);
    assertThat(WindowDefinition.sliding(3, 3).slide()).isEqualTo(3);
    assertThatThrownBy(() -> WindowDefinition.sliding(3, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> WindowDefinition.sliding(3, 4))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aTumblingWindowSlidesByItsWholeCount() {
    assertThat(WindowDefinition.tumbling(5).slide()).isEqualTo(5);
    assertThatThrownBy(() -> new WindowDefinition(WindowDefinition.Type.TUMBLING, 5, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theViewIsEmptyUntilTheWindowFirstFires() {
    EventWindow<Trade> sliding = EventWindow.ofTrades("trades", WindowDefinition.sliding(3));
    EventWindow<Trade> tumbling = EventWindow.ofTrades("trades", WindowDefinition.tumbling(3));

    sliding.append(events.trade(1, 1));
    tumbling.append(events.trade(1, 1));

    assertThat(sliding.view().events()).isEmpty();
    assertThat(tumbling.view().events()).isEmpty();
  }

  @Test
  void aSlidingWindowOfOneIsEveryUpdate() {
    EventWindow<BestBidAsk> window =
        EventWindow.ofBestBidAsks("quotes", WindowDefinition.sliding(1));

    for (int id = 1; id <= 3; id++) {
      Window<BestBidAsk> fired = window.append(events.bestBidAsk(id, id));

      assertThat(fired).isSameAs(window.view());
      assertThat(window.view().events()).hasSize(1);
      assertThat(window.view().events()[0].bookUpdateId()).isEqualTo(id);
    }
  }

  @Test
  void aWindowThatDoesNotFireReturnsNull() {
    EventWindow<BestBidAsk> window =
        EventWindow.ofBestBidAsks("quotes", WindowDefinition.tumbling(2));

    assertThat(window.append(events.bestBidAsk(1, 1))).isNull();
  }

  @Test
  void aTumblingWindowFiresOnceEverySliceAndHoldsTheLastClosedOne() {
    EventWindow<Trade> window = EventWindow.ofTrades("trades", WindowDefinition.tumbling(3));
    List<Integer> firedAt = new ArrayList<>();

    for (int id = 1; id <= 8; id++) {
      if (window.append(events.trade(id, id)) != null) {
        firedAt.add(id);
      }
    }

    assertThat(firedAt).containsExactly(3, 6);
    assertThat(ids(window.view())).containsExactly(4L, 5L, 6L);
  }

  @Test
  void theClosedSliceStaysIntactWhileTheNextOneFills() {
    EventWindow<Trade> window = EventWindow.ofTrades("trades", WindowDefinition.tumbling(3));

    for (int id = 1; id <= 5; id++) {
      window.append(events.trade(id, id));
    }

    assertThat(ids(window.view())).containsExactly(1L, 2L, 3L);
  }

  @Test
  void aTumblingWindowKeepsGoingForAnyNumberOfEvents() {
    EventWindow<Trade> window = EventWindow.ofTrades("trades", WindowDefinition.tumbling(100));

    for (int id = 1; id <= 1_050; id++) {
      window.append(events.trade(id, id));
    }

    Trade[] slice = window.view().events();
    assertThat(slice).hasSize(100);
    for (int i = 0; i < 100; i++) {
      assertThat(slice[i].tradeId()).isEqualTo(901L + i);
    }
  }

  @Test
  void aSlidingWindowKeepsGoingForAnyNumberOfEvents() {
    EventWindow<Trade> window = EventWindow.ofTrades("trades", WindowDefinition.sliding(7));

    for (int id = 1; id <= 1_000; id++) {
      window.append(events.trade(id, id));
    }

    assertThat(ids(window.view())).containsExactly(994L, 995L, 996L, 997L, 998L, 999L, 1_000L);
  }

  @Test
  void theViewIsAlwaysTheSameObject() {
    EventWindow<Trade> window = EventWindow.ofTrades("trades", WindowDefinition.tumbling(2));
    Window<Trade> view = window.view();

    for (int id = 1; id <= 6; id++) {
      Window<Trade> fired = window.append(events.trade(id, id));
      if (fired != null) {
        assertThat(fired).isSameAs(view);
      }
    }
  }

  @Test
  void keepsCopiesSoTheEventItWasGivenCanBeReused() {
    EventWindow<Trade> window = EventWindow.ofTrades("trades", WindowDefinition.sliding(1));
    Trade source = events.trade(1, 1);

    window.append(source);
    events.trade(2, 2); // the same object, overwritten as a ring slot would be

    assertThat(window.view().events()[0]).isNotSameAs(source);
    assertThat(window.view().events()[0].tradeId()).isEqualTo(1);
  }

  @Test
  void copiesEveryFieldOfATrade() {
    EventWindow<Trade> window = EventWindow.ofTrades("trades", WindowDefinition.sliding(1));

    window.append(events.trade(7, 50));

    Trade trade = window.view().events()[0];
    assertThat(trade.instrumentId()).isEqualTo(1);
    assertThat(trade.sequence()).isEqualTo(107);
    assertThat(trade.tradeId()).isEqualTo(7);
    assertThat(trade.priceMantissa()).isEqualTo(7);
    assertThat(trade.quantityMantissa()).isEqualTo(14);
    assertThat(trade.exchangeTimestamp()).isEqualTo(50);
    assertThat(trade.receiveTimestamp()).isEqualTo(51);
    assertThat(trade.aggressorSide()).isEqualTo(AggressorSide.SELL);
  }

  @Test
  void copiesEveryFieldOfATopOfBookUpdate() {
    EventWindow<BestBidAsk> window =
        EventWindow.ofBestBidAsks("quotes", WindowDefinition.sliding(1));

    window.append(events.bestBidAsk(7, 50));

    BestBidAsk update = window.view().events()[0];
    assertThat(update.instrumentId()).isEqualTo(2);
    assertThat(update.sequence()).isEqualTo(107);
    assertThat(update.bookUpdateId()).isEqualTo(7);
    assertThat(update.bidPriceMantissa()).isEqualTo(7);
    assertThat(update.bidQuantityMantissa()).isEqualTo(8);
    assertThat(update.askPriceMantissa()).isEqualTo(9);
    assertThat(update.askQuantityMantissa()).isEqualTo(10);
    assertThat(update.exchangeTimestamp()).isEqualTo(50);
    assertThat(update.receiveTimestamp()).isEqualTo(51);
  }

  @Test
  void followsArrivalOrderAtAnySpeed() {
    // A day of events replayed in a loop: windows only count events, the clock never matters
    EventWindow<Trade> window = EventWindow.ofTrades("trades", WindowDefinition.sliding(3));

    for (int id = 1; id <= 1_000; id++) {
      window.append(events.trade(id, id * 86L * SECOND));
    }

    assertThat(ids(window.view())).containsExactly(998L, 999L, 1_000L);
    assertThat(window.view().now()).isEqualTo(1_000 * 86L * SECOND);
  }

  @Test
  void nowNeverGoesBackwardsAndLateEventsAreNotReordered() {
    EventWindow<Trade> window = EventWindow.ofTrades("trades", WindowDefinition.sliding(3));

    assertThat(window.view().now()).isEqualTo(Window.NO_TIMESTAMP);
    window.append(events.trade(1, 10 * SECOND));
    window.append(events.trade(2, 5 * SECOND));
    window.append(events.trade(3, 11 * SECOND));

    assertThat(ids(window.view())).containsExactly(1L, 2L, 3L);
    assertThat(window.view().now()).isEqualTo(11 * SECOND);
  }

  @Test
  void nowOfATumblingWindowFollowsEveryEvent() {
    EventWindow<Trade> window = EventWindow.ofTrades("trades", WindowDefinition.tumbling(2));

    window.append(events.trade(1, 1));
    window.append(events.trade(2, 2));
    window.append(events.trade(3, 3));

    assertThat(window.view().now()).isEqualTo(3);
    assertThat(ids(window.view())).containsExactly(1L, 2L);
  }

  @Test
  void aWindowNeedsAPositiveCount() {
    assertThatThrownBy(() -> WindowDefinition.sliding(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> WindowDefinition.tumbling(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** Appends trades with ids 1 to {@code events} and returns the ids at which the window fired. */
  private List<Integer> firedAt(EventWindow<Trade> window, int events) {
    List<Integer> fired = new ArrayList<>();
    for (int id = 1; id <= events; id++) {
      if (window.append(this.events.trade(id, id)) != null) {
        fired.add(id);
      }
    }
    return fired;
  }

  private static List<Long> ids(Window<Trade> window) {
    List<Long> ids = new ArrayList<>();
    for (Trade trade : window.events()) {
      ids.add(trade.tradeId());
    }
    return ids;
  }
}
