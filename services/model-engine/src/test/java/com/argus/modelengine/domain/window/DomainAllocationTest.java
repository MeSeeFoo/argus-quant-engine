package com.argus.modelengine.domain.window;

import static org.assertj.core.api.Assertions.assertThat;

import com.argus.modelengine.domain.feed.Deduplicator;
import com.argus.modelengine.domain.feed.SequenceGapDetector;
import com.argus.modelengine.domain.instrument.Instrument;
import com.argus.modelengine.domain.instrument.Instruments;
import com.argus.modelengine.domain.marketdata.BestBidAsk;
import com.argus.modelengine.domain.marketdata.Trade;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Appending to windows, firing them, reading them and the reader's checks must not allocate.
 * Measured with the JVM's per-thread allocation counter: any allocation per event would show up as
 * hundreds of kilobytes over the loop, and the tolerance scales with the events.
 */
class DomainAllocationTest {

  private static final int EVENTS = 200_000;
  private static final long TOLERATED_BYTES = EVENTS / 100;

  private final TestEvents events = new TestEvents();
  private final EventWindow<Trade> sliding =
      EventWindow.ofTrades("trades", WindowDefinition.sliding(100));
  private final EventWindow<Trade> tumbling =
      EventWindow.ofTrades("trades", WindowDefinition.tumbling(50));
  private final EventWindow<BestBidAsk> quotes =
      EventWindow.ofBestBidAsks("quotes", WindowDefinition.tumbling(50));
  private final Deduplicator deduplicator = new Deduplicator(1);
  private final SequenceGapDetector gaps = new SequenceGapDetector();
  private final Instruments instruments =
      new Instruments(List.of(Instrument.parse("BINANCE:BTCUSDT")));
  private final byte[] venue =
      Arrays.copyOf("BINANCE".getBytes(StandardCharsets.US_ASCII), Instrument.VENUE_LENGTH);
  private final byte[] symbol =
      Arrays.copyOf("BTCUSDT".getBytes(StandardCharsets.US_ASCII), Instrument.SYMBOL_LENGTH);
  private long nextId;
  private long sum;
  private long fired;
  private long accepted;

  @Test
  void allocatesNothingInSteadyState() {
    var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    run(); // warm-up, so the JIT has compiled the loop
    long before = threads.getCurrentThreadAllocatedBytes();
    run();
    long allocated = threads.getCurrentThreadAllocatedBytes() - before;

    assertThat(accepted).as("each id accepted once, the repeat dropped").isEqualTo(EVENTS * 2L);
    assertThat(sum).isNotZero();
    assertThat(fired).isNotZero();
    assertThat(allocated).isLessThan(TOLERATED_BYTES);
  }

  private void run() {
    for (int i = 0; i < EVENTS; i++) {
      long id = nextId++;
      int instrumentId = instruments.indexOf(venue, symbol);
      if (deduplicator.accept(instrumentId, id)) {
        accepted++;
      }
      if (deduplicator.accept(instrumentId, id)) {
        accepted++;
      }
      sum += gaps.onMessage(1, id);
      Trade trade = events.trade(id, id);
      sum += read(sliding.append(trade)) + read(tumbling.append(trade));
      Window<BestBidAsk> firedQuotes = quotes.append(events.bestBidAsk(id, id));
      if (firedQuotes != null) {
        fired++;
        BestBidAsk[] slice = firedQuotes.events();
        sum += slice[slice.length - 1].bidPriceMantissa() + firedQuotes.now();
      }
    }
  }

  private static long read(@Nullable Window<Trade> window) {
    if (window == null) {
      return 1;
    }
    Trade[] trades = window.events();
    return trades[trades.length - 1].priceMantissa() + trades.length;
  }
}
