package com.argus.modelengine.domain;

import static com.argus.modelengine.domain.TestEvents.SECOND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventWindowTest {

  private final TestEvents events = new TestEvents();

  @Test
  void aSlidingWindowHoldsTheLastEventsAndFiresOnEachOne() {
    TradeStore store = new TradeStore(3);
    EventWindow<Trade> window = store.window(WindowDefinition.sliding(3));

    for (int id = 1; id <= 5; id++) {
      store.append(events.trade(id, id));
      assertThat(window.hasFired()).isTrue();
    }

    assertThat(ids(window)).containsExactly(3L, 4L, 5L);
  }

  @Test
  void aSlidingWindowHoldsWhatItHasWhileFillingUp() {
    TradeStore store = new TradeStore(3);
    EventWindow<Trade> window = store.window(WindowDefinition.sliding(3));

    assertThat(window.size()).isZero();
    store.append(events.trade(1, 1));
    store.append(events.trade(2, 2));

    assertThat(ids(window)).containsExactly(1L, 2L);
  }

  @Test
  void aSlidingWindowOfOneIsEveryUpdate() {
    BestBidAskStore store = new BestBidAskStore(1);
    EventWindow<BestBidAsk> window = store.window(WindowDefinition.sliding(1));

    for (int id = 1; id <= 3; id++) {
      store.append(events.bestBidAsk(id, id));

      assertThat(window.hasFired()).isTrue();
      assertThat(window.size()).isEqualTo(1);
      assertThat(window.get(0).bookUpdateId()).isEqualTo(id);
    }
  }

  @Test
  void aTumblingWindowFiresOnceEverySliceAndReadsTheLastClosedOne() {
    TradeStore store = new TradeStore(5);
    EventWindow<Trade> window = store.window(WindowDefinition.tumbling(3));
    List<Integer> firedAt = new ArrayList<>();

    for (int id = 1; id <= 8; id++) {
      store.append(events.trade(id, id));
      if (window.hasFired()) {
        firedAt.add(id);
      }
    }

    assertThat(firedAt).containsExactly(3, 6);
    assertThat(ids(window)).containsExactly(4L, 5L, 6L);
  }

  @Test
  void aTumblingWindowIsEmptyUntilItsFirstSliceCloses() {
    TradeStore store = new TradeStore(5);
    EventWindow<Trade> window = store.window(WindowDefinition.tumbling(3));

    store.append(events.trade(1, 1));
    store.append(events.trade(2, 2));

    assertThat(window.hasFired()).isFalse();
    assertThat(window.size()).isZero();
  }

  @Test
  void theClosedSliceStaysIntactWhileTheNextOneFills() {
    // 2N - 1: the smallest store a tumbling window of N accepts
    TradeStore store = new TradeStore(5);
    EventWindow<Trade> window = store.window(WindowDefinition.tumbling(3));

    for (int id = 1; id <= 5; id++) {
      store.append(events.trade(id, id));
    }

    assertThat(window.hasFired()).isFalse();
    assertThat(ids(window)).containsExactly(1L, 2L, 3L);
  }

  @Test
  void fitsInTheSizeThePlannerGivesItForAnyNumberOfEvents() {
    TradeStore store = new TradeStore(WindowDefinition.tumbling(100).requiredCapacity());
    EventWindow<Trade> window = store.window(WindowDefinition.tumbling(100));

    for (int id = 1; id <= 1_050; id++) {
      store.append(events.trade(id, id));
    }

    assertThat(store.capacity()).isEqualTo(199);
    assertThat(window.size()).isEqualTo(100);
    for (int i = 0; i < 100; i++) {
      assertThat(window.get(i).tradeId()).isEqualTo(901L + i);
    }
  }

  @Test
  void windowsOnOneStoreShareTheSameObjects() {
    TradeStore store = new TradeStore(4);
    EventWindow<Trade> sliding = store.window(WindowDefinition.sliding(2));
    EventWindow<Trade> tumbling = store.window(WindowDefinition.tumbling(2));

    store.append(events.trade(1, 1));
    store.append(events.trade(2, 2));

    assertThat(sliding.get(1)).isSameAs(tumbling.get(1));
  }

  @Test
  void followsArrivalOrderAndEventTimeAtAnySpeed() {
    // A day of events replayed in a loop: windows only count events, the clock never matters
    TradeStore store = new TradeStore(3);
    EventWindow<Trade> window = store.window(WindowDefinition.sliding(3));

    for (int id = 1; id <= 1_000; id++) {
      store.append(events.trade(id, id * 86L * SECOND));
    }

    assertThat(ids(window)).containsExactly(998L, 999L, 1_000L);
    assertThat(window.now()).isEqualTo(1_000 * 86 * SECOND);
  }

  @Test
  void nowNeverGoesBackwardsAndLateEventsAreNotReordered() {
    TradeStore store = new TradeStore(3);
    EventWindow<Trade> window = store.window(WindowDefinition.sliding(3));

    assertThat(window.now()).isEqualTo(EventWindow.NO_TIMESTAMP);
    store.append(events.trade(1, 10 * SECOND));
    store.append(events.trade(2, 5 * SECOND));
    store.append(events.trade(3, 11 * SECOND));

    assertThat(ids(window)).containsExactly(1L, 2L, 3L);
    assertThat(window.now()).isEqualTo(11 * SECOND);
  }

  @Test
  void aWindowCreatedLaterStartsWithTheNextEvent() {
    TradeStore store = new TradeStore(3);
    store.append(events.trade(1, 1));

    EventWindow<Trade> window = store.window(WindowDefinition.sliding(3));
    store.append(events.trade(2, 2));

    assertThat(ids(window)).containsExactly(2L);
  }

  @Test
  void rejectsAnIndexOutsideTheWindow() {
    TradeStore store = new TradeStore(2);
    EventWindow<Trade> window = store.window(WindowDefinition.sliding(2));
    store.append(events.trade(1, 1));

    assertThatThrownBy(() -> window.get(1)).isInstanceOf(IndexOutOfBoundsException.class);
  }

  @Test
  void aWindowNeedsAPositiveCount() {
    assertThatThrownBy(() -> WindowDefinition.sliding(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> WindowDefinition.tumbling(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void requiredCapacityFollowsTheWindowType() {
    assertThat(WindowDefinition.sliding(100).requiredCapacity()).isEqualTo(100);
    assertThat(WindowDefinition.tumbling(100).requiredCapacity()).isEqualTo(199);
    assertThat(WindowDefinition.tumbling(1).requiredCapacity()).isEqualTo(1);
  }

  private static List<Long> ids(EventWindow<Trade> window) {
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < window.size(); i++) {
      ids.add(window.get(i).tradeId());
    }
    return ids;
  }
}
