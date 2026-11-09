package com.argus.modelengine.domain;

import static com.argus.modelengine.domain.TestEvents.SECOND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EventWindowTest {

  private static final long MINUTE = 60 * SECOND;
  // 2026-11-09, on a minute boundary: real timestamps must not be mistaken for small numbers
  private static final long BASE = 1_794_000_000L * SECOND;

  private final TestEvents events = new TestEvents();
  private final TradeStore store = new TradeStore(16, 1 << 16);

  private void trade(long id, long timestamp) {
    store.append(events.trade(id, timestamp));
  }

  private static long[] ids(EventWindow<Trade> window) {
    long[] ids = new long[window.size()];
    for (int i = 0; i < ids.length; i++) {
      ids[i] = window.get(i).tradeId();
    }
    return ids;
  }

  private static long[] openIds(TumblingWindow<Trade> window) {
    long[] ids = new long[window.openSize()];
    for (int i = 0; i < ids.length; i++) {
      ids[i] = window.getOpen(i).tradeId();
    }
    return ids;
  }

  @Test
  void aSlidingTimeWindowHoldsTheLastDurationOfEventTime() {
    EventWindow<Trade> window = store.slidingTime(SECOND);

    trade(1, BASE);
    trade(2, BASE + SECOND / 2);
    trade(3, BASE + SECOND);

    // (now - duration, now]: the event exactly one second old has left
    assertThat(ids(window)).containsExactly(2, 3);
    assertThat(window.now()).isEqualTo(BASE + SECOND);
  }

  @Test
  void aOneMinuteSlidingWindowFollowsEventTimeWhateverTheArrivalRate() {
    EventWindow<Trade> window = store.slidingTime(MINUTE);

    // Ten minutes of event time arrive within this test: only event time moves the window
    for (int second = 0; second < 600; second++) {
      trade(second, BASE + second * SECOND);
    }

    assertThat(window.size()).isEqualTo(60);
    assertThat(window.get(0).tradeId()).isEqualTo(540);
    assertThat(window.get(59).tradeId()).isEqualTo(599);
  }

  @Test
  void anEmptySlidingWindowHasNoNow() {
    EventWindow<Trade> window = store.slidingTime(SECOND);

    assertThat(window.size()).isZero();
    assertThat(window.now()).isEqualTo(EventWindow.NO_TIMESTAMP);
    assertThat(window.isComplete()).isTrue();
  }

  @Test
  void nowNeverGoesBackwards() {
    EventWindow<Trade> window = store.slidingTime(10 * SECOND);

    trade(1, BASE + 5 * SECOND);
    trade(2, BASE + 2 * SECOND);

    assertThat(window.now()).isEqualTo(BASE + 5 * SECOND);
    assertThat(window.lateEvents()).isEqualTo(1);
  }

  @Test
  void aLateEventStaysWhereItArrived() {
    EventWindow<Trade> window = store.slidingTime(10 * SECOND);

    trade(1, BASE + 5 * SECOND);
    trade(2, BASE + 2 * SECOND);
    trade(3, BASE + 6 * SECOND);

    assertThat(ids(window)).containsExactly(1, 2, 3);
  }

  @Test
  void aLateEventOlderThanTheWholeWindowStaysUntilTheStartReachesIt() {
    EventWindow<Trade> window = store.slidingTime(SECOND);

    trade(1, BASE + 10 * SECOND);
    trade(2, BASE + 12 * SECOND);
    trade(3, BASE);

    assertThat(ids(window)).containsExactly(2, 3);

    trade(4, BASE + 13 * SECOND);

    assertThat(ids(window)).containsExactly(4);
  }

  @Test
  void aSlidingCountWindowHoldsTheLastEvents() {
    EventWindow<Trade> window = store.slidingCount(3);

    trade(1, BASE);
    assertThat(ids(window)).containsExactly(1);

    trade(2, BASE);
    trade(3, BASE);
    trade(4, BASE);

    assertThat(ids(window)).containsExactly(2, 3, 4);
    assertThat(window.isComplete()).isTrue();
  }

  @Test
  void aTumblingTimeWindowClosesWhenALaterSliceBegins() {
    TumblingTimeWindow<Trade> window = store.tumblingTime(SECOND);

    trade(1, BASE);
    trade(2, BASE + SECOND - 1);
    assertThat(window.size()).isZero();
    assertThat(window.closedSlices()).isZero();
    assertThat(window.closedStart()).isEqualTo(EventWindow.NO_TIMESTAMP);
    assertThat(openIds(window)).containsExactly(1, 2);

    trade(3, BASE + SECOND);

    assertThat(ids(window)).containsExactly(1, 2);
    assertThat(window.closedSlices()).isEqualTo(1);
    assertThat(window.closedStart()).isEqualTo(BASE);
    assertThat(openIds(window)).containsExactly(3);
  }

  @Test
  void aOneMinuteTumblingWindowIsAlignedToTheEpoch() {
    TumblingTimeWindow<Trade> window = store.tumblingTime(MINUTE);

    // 90 s of event time: the first slice ends at the minute, not 60 s after the first event
    for (int second = 30; second < 120; second++) {
      trade(second, BASE + second * SECOND);
    }

    assertThat(window.closedSlices()).isEqualTo(1);
    assertThat(window.size()).isEqualTo(30);
    assertThat(window.closedStart()).isEqualTo(BASE);
    assertThat(window.get(0).tradeId()).isEqualTo(30);
    assertThat(window.openSize()).isEqualTo(60);
  }

  @Test
  void aSliceWithNoEventsIsNeverCreated() {
    TumblingTimeWindow<Trade> window = store.tumblingTime(SECOND);

    trade(1, BASE);
    trade(2, BASE + 100 * SECOND);

    assertThat(window.closedSlices()).isEqualTo(1);
    assertThat(window.closedStart()).isEqualTo(BASE);
    assertThat(ids(window)).containsExactly(1);
  }

  @Test
  void aLateEventForAnOlderSliceStaysInTheOpenOne() {
    TumblingTimeWindow<Trade> window = store.tumblingTime(SECOND);

    trade(1, BASE);
    trade(2, BASE + SECOND);
    trade(3, BASE + SECOND / 2);

    assertThat(ids(window)).containsExactly(1);
    assertThat(openIds(window)).containsExactly(2, 3);
    assertThat(window.lateEvents()).isEqualTo(1);
  }

  @Test
  void aTumblingCountWindowClosesWithItsLastEvent() {
    TumblingWindow<Trade> window = store.tumblingCount(2);

    trade(1, BASE);
    assertThat(window.size()).isZero();
    assertThat(openIds(window)).containsExactly(1);

    trade(2, BASE);
    assertThat(ids(window)).containsExactly(1, 2);
    assertThat(window.openSize()).isZero();

    trade(3, BASE);
    trade(4, BASE);
    assertThat(ids(window)).containsExactly(3, 4);
    assertThat(window.closedSlices()).isEqualTo(2);
  }

  @Test
  void aTumblingCountWindowKeepsItsClosedSliceWhileTheNextOneFills() {
    TradeStore small = new TradeStore(2, 3);
    TumblingWindow<Trade> window = small.tumblingCount(2);

    for (int i = 1; i <= 3; i++) {
      small.append(events.trade(i, BASE));
    }

    assertThat(ids(window)).containsExactly(1, 2);
    assertThat(openIds(window)).containsExactly(3);
    assertThat(small.lostEvents()).isZero();
  }

  @Test
  void aSlidingTimeWindowReportsLostEventsUntilTheyWouldHaveLeft() {
    TradeStore small = new TradeStore(2, 2);
    EventWindow<Trade> window = small.slidingTime(10 * SECOND);

    small.append(events.trade(1, BASE));
    small.append(events.trade(2, BASE + SECOND));
    small.append(events.trade(3, BASE + 2 * SECOND));

    assertThat(window.isComplete()).isFalse();
    assertThat(ids(window)).containsExactly(2, 3);
    assertThat(small.lostEvents()).isEqualTo(1);

    small.append(events.trade(4, BASE + 11 * SECOND));

    assertThat(window.isComplete()).as("the lost event is older than the window now").isTrue();
  }

  @Test
  void aTumblingWindowReportsAnOpenSliceThatLostEventsWhenItCloses() {
    TradeStore small = new TradeStore(2, 2);
    TumblingTimeWindow<Trade> window = small.tumblingTime(MINUTE);

    small.append(events.trade(1, BASE));
    small.append(events.trade(2, BASE + SECOND));
    small.append(events.trade(3, BASE + 2 * SECOND));
    assertThat(window.isComplete()).as("no slice has closed yet").isTrue();

    small.append(events.trade(4, BASE + MINUTE));

    // The new event needs a slot too, so a store at its maximum loses one more
    assertThat(window.isComplete()).isFalse();
    assertThat(ids(window)).containsExactly(3);

    small.append(events.trade(5, BASE + 2 * MINUTE));

    assertThat(window.isComplete()).as("the next slice lost nothing").isTrue();
    assertThat(ids(window)).containsExactly(4);
  }

  @Test
  void aTumblingWindowReportsAClosedSliceThatLostEvents() {
    TradeStore small = new TradeStore(3, 3);
    TumblingTimeWindow<Trade> window = small.tumblingTime(MINUTE);

    small.append(events.trade(1, BASE));
    small.append(events.trade(2, BASE + SECOND));
    small.append(events.trade(3, BASE + MINUTE));
    small.append(events.trade(4, BASE + MINUTE + SECOND));

    assertThat(window.isComplete()).isFalse();
    assertThat(ids(window)).containsExactly(2);
    assertThat(openIds(window)).containsExactly(3, 4);
  }

  @Test
  void aWindowOnlySeesEventsFromWhenItWasCreated() {
    trade(1, BASE);

    EventWindow<Trade> window = store.slidingCount(5);
    trade(2, BASE);

    assertThat(ids(window)).containsExactly(2);
  }

  @Test
  void windowsOverOneStoreShareTheSameObjects() {
    EventWindow<Trade> byTime = store.slidingTime(MINUTE);
    EventWindow<Trade> byCount = store.slidingCount(1);

    trade(1, BASE);

    assertThat(byTime.get(0)).isSameAs(byCount.get(0));
  }

  @Test
  void readingOutsideTheWindowFails() {
    EventWindow<Trade> window = store.slidingCount(2);
    TumblingWindow<Trade> tumbling = store.tumblingCount(2);
    trade(1, BASE);

    assertThatThrownBy(() -> window.get(1)).isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> window.get(-1)).isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> tumbling.getOpen(1)).isInstanceOf(IndexOutOfBoundsException.class);
  }

  @Test
  void rejectsWindowsWithoutALength() {
    assertThatThrownBy(() -> store.slidingTime(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> store.slidingCount(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> store.tumblingTime(-1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> store.tumblingCount(0)).isInstanceOf(IllegalArgumentException.class);
  }
}
