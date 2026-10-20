package com.argus.feedgateway.adapter.in.binance;

import static org.assertj.core.api.Assertions.assertThat;

import com.argus.feedgateway.adapter.in.binance.BinanceFrames.Trade;
import com.argus.feedgateway.adapter.in.binance.sbe.BestBidAskStreamEventEncoder;
import com.argus.feedgateway.adapter.in.binance.sbe.MessageHeaderEncoder;
import com.argus.feedgateway.application.MarketDataCounters;
import com.argus.feedgateway.application.MarketDataNormaliser;
import com.argus.feedgateway.application.MarketDataPublisher;
import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.Instrument;
import com.argus.feedgateway.domain.Instruments;
import java.lang.management.ManagementFactory;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The decoding path must not allocate per frame. Measured with the JVM's per-thread allocation
 * counter: any allocation per frame would show up as hundreds of kilobytes over the loop.
 */
class BinanceStreamDecoderAllocationTest {

  private static final int FRAMES = 100_000;
  private static final long TOLERATED_BYTES = 1_024;

  private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024]);
  private final CountingPublisher publisher = new CountingPublisher();
  private final MarketDataCounters counters = new MarketDataCounters();
  private final Instruments instruments =
      new Instruments(
          List.of(new Instrument("BINANCE", "ETHUSDT"), new Instrument("BINANCE", "BTCUSDT")));
  private final BinanceStreamDecoder decoder =
      new BinanceStreamDecoder(
          instruments, new MarketDataNormaliser(instruments, publisher, counters), counters);
  private final BestBidAskStreamEventEncoder bookUpdateIds = new BestBidAskStreamEventEncoder();
  private long bookUpdateId;

  @Test
  void decodesWithoutAllocating() {
    BinanceFrames frames = new BinanceFrames();
    int tradesLength =
        frames.trades(
            buffer,
            0,
            "BTCUSDT",
            1,
            1,
            -2,
            -8,
            new Trade(1, 6421050, 1, true),
            new Trade(2, 6421051, 2, false));
    int bookOffset = 512;
    var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    int bookLength = frames.bestBidAsk(buffer, bookOffset, "BTCUSDT", 1, 0, -2, -5, 1, 1, 2, 1);
    bookUpdateIds.wrap(buffer, bookOffset + MessageHeaderEncoder.ENCODED_LENGTH);

    decodeFrames(tradesLength, bookOffset, bookLength); // warm-up
    long before = threads.getCurrentThreadAllocatedBytes();
    decodeFrames(tradesLength, bookOffset, bookLength);
    long allocated = threads.getCurrentThreadAllocatedBytes() - before;

    assertThat(publisher.trades).isEqualTo(4L * FRAMES);
    assertThat(publisher.bestBidAsks).isEqualTo(2L * FRAMES);
    assertThat(allocated).isLessThan(TOLERATED_BYTES);
  }

  private void decodeFrames(int tradesLength, int bookOffset, int bookLength) {
    for (int i = 0; i < FRAMES; i++) {
      decoder.onFrame(buffer, 0, tradesLength, i);
      // A newer book version each time, written in place so the test allocates nothing either
      bookUpdateIds.bookUpdateId(++bookUpdateId);
      decoder.onFrame(buffer, bookOffset, bookLength, i);
    }
  }

  private static final class CountingPublisher implements MarketDataPublisher {

    long trades;
    long bestBidAsks;

    @Override
    public void publishTrade(
        Instrument instrument,
        long tradeId,
        long price,
        long quantity,
        long exchangeTimestamp,
        long receiveTimestamp,
        AggressorSide aggressorSide) {
      trades++;
    }

    @Override
    public void publishBestBidAsk(
        Instrument instrument,
        long bookUpdateId,
        long bidPrice,
        long bidQuantity,
        long askPrice,
        long askQuantity,
        long exchangeTimestamp,
        long receiveTimestamp) {
      bestBidAsks++;
    }
  }
}
