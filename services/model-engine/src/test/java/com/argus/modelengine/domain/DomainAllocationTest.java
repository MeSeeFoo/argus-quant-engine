package com.argus.modelengine.domain;

import static com.argus.modelengine.domain.TestEvents.SECOND;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Storing events, moving windows, reading them and the reader's checks must not allocate once the
 * stores have settled. Measured with the JVM's per-thread allocation counter: any allocation per
 * event would show up as hundreds of kilobytes over the loop, and the tolerance scales with the
 * events.
 */
class DomainAllocationTest {

  private static final int EVENTS = 200_000;
  private static final long TOLERATED_BYTES = EVENTS / 100;
  private static final long NANOS_PER_EVENT = SECOND / 1_000;

  private final TestEvents events = new TestEvents();
  private final TradeStore trades = new TradeStore(16, 1 << 16);
  private final BestBidAskStore quotes = new BestBidAskStore(16, 1 << 16);
  private final EventWindow<Trade> slidingTime = trades.slidingTime(SECOND);
  private final EventWindow<Trade> slidingCount = trades.slidingCount(100);
  private final TumblingTimeWindow<Trade> tumblingTime = trades.tumblingTime(SECOND);
  private final TumblingWindow<Trade> tumblingCount = trades.tumblingCount(50);
  private final EventWindow<BestBidAsk> quoteWindow = quotes.slidingTime(SECOND);
  private final TradeDeduplicator deduplicator = new TradeDeduplicator(1);
  private final SequenceGapCounter gaps = new SequenceGapCounter();
  private final Instruments instruments =
      new Instruments(List.of(Instrument.parse("BINANCE:BTCUSDT")));
  private final byte[] venue =
      Arrays.copyOf("BINANCE".getBytes(StandardCharsets.US_ASCII), Instrument.VENUE_LENGTH);
  private final byte[] symbol =
      Arrays.copyOf("BTCUSDT".getBytes(StandardCharsets.US_ASCII), Instrument.SYMBOL_LENGTH);
  private long nextId;
  private long sum;

  @Test
  void allocatesNothingInSteadyState() {
    var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    run(); // warm-up: the stores grow to the size their windows need
    int settledCapacity = trades.capacity();
    long before = threads.getCurrentThreadAllocatedBytes();
    run();
    long allocated = threads.getCurrentThreadAllocatedBytes() - before;

    assertThat(trades.capacity()).isEqualTo(settledCapacity);
    assertThat(trades.lostEvents()).isZero();
    assertThat(deduplicator.droppedTrades()).isEqualTo(EVENTS * 2L);
    assertThat(sum).isNotZero();
    assertThat(allocated).isLessThan(TOLERATED_BYTES);
  }

  private void run() {
    for (int i = 0; i < EVENTS; i++) {
      long id = nextId++;
      long timestamp = id * NANOS_PER_EVENT;
      int instrumentId = instruments.indexOf(venue, symbol);
      deduplicator.accept(instrumentId, id);
      deduplicator.accept(instrumentId, id);
      gaps.onMessage(1, id);
      trades.append(events.trade(id, timestamp));
      quotes.append(events.bestBidAsk(id, timestamp));
      sum += read(slidingTime) + read(slidingCount) + read(tumblingTime) + read(tumblingCount);
      sum += quoteWindow.get(quoteWindow.size() - 1).bidPrice();
    }
  }

  private static long read(EventWindow<Trade> window) {
    return window.size() == 0 ? 1 : window.get(window.size() - 1).price() + window.size();
  }
}
