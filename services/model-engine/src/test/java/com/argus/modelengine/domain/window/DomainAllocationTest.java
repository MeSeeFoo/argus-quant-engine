package com.argus.modelengine.domain.window;

import static org.assertj.core.api.Assertions.assertThat;

import com.argus.modelengine.domain.feed.SequenceGapCounter;
import com.argus.modelengine.domain.feed.TradeDeduplicator;
import com.argus.modelengine.domain.instrument.Instrument;
import com.argus.modelengine.domain.instrument.Instruments;
import com.argus.modelengine.domain.marketdata.BestBidAsk;
import com.argus.modelengine.domain.marketdata.Trade;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Storing events, moving windows, reading them and the reader's checks must not allocate. Measured
 * with the JVM's per-thread allocation counter: any allocation per event would show up as hundreds
 * of kilobytes over the loop, and the tolerance scales with the events.
 */
class DomainAllocationTest {

  private static final int EVENTS = 200_000;
  private static final long TOLERATED_BYTES = EVENTS / 100;
  private static final WindowDefinition SLIDING = WindowDefinition.sliding(100);
  private static final WindowDefinition TUMBLING = WindowDefinition.tumbling(50);

  private final TestEvents events = new TestEvents();
  private final TradeStore trades = new TradeStore(SLIDING.requiredCapacity());
  private final BestBidAskStore quotes = new BestBidAskStore(TUMBLING.requiredCapacity());
  private final EventWindow<Trade> sliding = trades.window(SLIDING);
  private final EventWindow<Trade> tumbling = trades.window(TUMBLING);
  private final EventWindow<BestBidAsk> quoteWindow = quotes.window(TUMBLING);
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
  private long fired;

  @Test
  void allocatesNothingInSteadyState() {
    var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    run(); // warm-up, so the JIT has compiled the loop
    long before = threads.getCurrentThreadAllocatedBytes();
    run();
    long allocated = threads.getCurrentThreadAllocatedBytes() - before;

    assertThat(deduplicator.droppedTrades()).isEqualTo(EVENTS * 2L);
    assertThat(sum).isNotZero();
    assertThat(fired).isNotZero();
    assertThat(allocated).isLessThan(TOLERATED_BYTES);
  }

  private void run() {
    for (int i = 0; i < EVENTS; i++) {
      long id = nextId++;
      int instrumentId = instruments.indexOf(venue, symbol);
      deduplicator.accept(instrumentId, id);
      deduplicator.accept(instrumentId, id);
      gaps.onMessage(1, id);
      trades.append(events.trade(id, id));
      quotes.append(events.bestBidAsk(id, id));
      sum += read(sliding) + read(tumbling) + sliding.now();
      if (quoteWindow.hasFired()) {
        fired++;
        sum += quoteWindow.get(quoteWindow.size() - 1).bidPrice();
      }
    }
  }

  private static long read(EventWindow<Trade> window) {
    return window.size() == 0 ? 1 : window.get(window.size() - 1).price() + window.size();
  }
}
