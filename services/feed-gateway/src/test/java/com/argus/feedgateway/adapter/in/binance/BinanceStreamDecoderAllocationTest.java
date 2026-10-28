package com.argus.feedgateway.adapter.in.binance;

import static org.assertj.core.api.Assertions.assertThat;

import com.argus.feedgateway.adapter.in.binance.BinanceFrames.Trade;
import com.argus.feedgateway.adapter.in.binance.sbe.BestBidAskStreamEventEncoder;
import com.argus.feedgateway.adapter.in.binance.sbe.MessageHeaderEncoder;
import com.argus.feedgateway.application.MarketDataCounters;
import com.argus.feedgateway.application.MarketDataQueue;
import com.argus.feedgateway.application.MarketDataRelay;
import com.argus.feedgateway.application.port.out.MarketDataPublisher;
import com.argus.feedgateway.domain.BestBidAsk;
import com.argus.feedgateway.domain.Instrument;
import com.argus.feedgateway.domain.Instruments;
import java.lang.management.ManagementFactory;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The whole path of a frame must not allocate: decoding, queueing, draining and relaying. Measured
 * with the JVM's per-thread allocation counter: any allocation per frame would show up as hundreds
 * of kilobytes over the loop.
 */
class BinanceStreamDecoderAllocationTest {

  private static final int FRAMES = 100_000;
  private static final long TOLERATED_BYTES = 1_024;

  private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024]);
  private final CountingPublisher publisher = new CountingPublisher();
  private final BinanceCounters counters = new BinanceCounters();
  private final MarketDataCounters relayCounters = new MarketDataCounters();
  private final Instruments instruments =
      new Instruments(
          List.of(new Instrument("BINANCE", "ETHUSDT"), new Instrument("BINANCE", "BTCUSDT")));
  private final MarketDataQueue queue =
      new MarketDataQueue(
          instruments,
          new MarketDataRelay(instruments, publisher, relayCounters, 1_000_000_000L),
          relayCounters,
          64 * 1024);
  private final BinanceStreamDecoder decoder =
      new BinanceStreamDecoder(instruments, queue, counters);
  private final BestBidAskStreamEventEncoder bookUpdateIds = new BestBidAskStreamEventEncoder();
  private long bookUpdateId;
  private Frame trades = new Frame(0, 0);
  private Frame unknownSymbol = trades;
  private Frame unrepresentable = trades;
  private Frame book = trades;

  @Test
  void decodesWithoutAllocating() {
    // Every frame is written once, before measuring: encoding a symbol allocates.
    BinanceFrames frames = new BinanceFrames();
    trades =
        new Frame(
            0,
            frames.trades(
                buffer,
                0,
                "BTCUSDT",
                1,
                1,
                -2,
                -8,
                new Trade(1, 6421050, 1, true),
                new Trade(2, 6421051, 2, false)));
    unknownSymbol =
        new Frame(
            256, frames.trades(buffer, 256, "SOLUSDT", 1, 1, -2, -8, new Trade(1, 1, 1, true)));
    unrepresentable =
        new Frame(
            384, frames.trades(buffer, 384, "BTCUSDT", 1, 1, -9, -8, new Trade(1, 1, 1, true)));
    book = new Frame(512, frames.bestBidAsk(buffer, 512, "BTCUSDT", 1, 0, -2, -5, 1, 1, 2, 1));
    bookUpdateIds.wrap(buffer, book.offset() + MessageHeaderEncoder.ENCODED_LENGTH);
    var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    decodeFrames(); // warm-up
    long before = threads.getCurrentThreadAllocatedBytes();
    decodeFrames();
    long allocated = threads.getCurrentThreadAllocatedBytes() - before;

    assertThat(publisher.trades).isEqualTo(4L * FRAMES);
    assertThat(publisher.bestBidAsks).isEqualTo(2L * FRAMES);
    assertThat(relayCounters.staleBookUpdates()).isEqualTo(2L * FRAMES);
    assertThat(counters.unknownSymbols()).isEqualTo(2L * FRAMES);
    assertThat(relayCounters.unrepresentableValues()).isEqualTo(2L * FRAMES);
    assertThat(allocated).isLessThan(TOLERATED_BYTES);
  }

  /** Accepted and rejected frames alike: none of them may allocate. */
  private void decodeFrames() {
    for (int i = 0; i < FRAMES; i++) {
      decode(trades, i);
      decode(unknownSymbol, i);
      decode(unrepresentable, i);
      // A newer book version, written in place, then the same one again, which is stale
      bookUpdateIds.bookUpdateId(++bookUpdateId);
      decode(book, i);
      decode(book, i);
    }
  }

  private void decode(Frame frame, long receiveTimestamp) {
    decoder.onFrame(buffer, frame.offset(), frame.length(), receiveTimestamp);
    queue.drain(Integer.MAX_VALUE);
  }

  private record Frame(int offset, int length) {}

  private static final class CountingPublisher implements MarketDataPublisher {

    long trades;
    long bestBidAsks;

    @Override
    public void publishTrade(com.argus.feedgateway.domain.Trade trade) {
      trades++;
    }

    @Override
    public void publishBestBidAsk(BestBidAsk bestBidAsk) {
      bestBidAsks++;
    }

    @Override
    public void publishHeartbeat(long epochNow) {}
  }
}
