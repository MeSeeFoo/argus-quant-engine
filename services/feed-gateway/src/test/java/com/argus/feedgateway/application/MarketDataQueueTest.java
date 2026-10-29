package com.argus.feedgateway.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.BestBidAsk;
import com.argus.feedgateway.domain.Instrument;
import com.argus.feedgateway.domain.Instruments;
import com.argus.feedgateway.domain.Trade;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

class MarketDataQueueTest {

  private static final Instrument BTCUSDT = new Instrument("BINANCE", "BTCUSDT");
  private static final Instrument ETHUSDT = new Instrument("BINANCE", "ETHUSDT");
  private static final int CAPACITY = 64 * 1024;

  private final Instruments instruments = new Instruments(List.of(BTCUSDT, ETHUSDT));
  private final RecordingPublisher publisher = new RecordingPublisher();
  private final MarketDataCounters counters = new MarketDataCounters();
  private final MarketDataRelay relay =
      new MarketDataRelay(instruments, publisher, counters, 1_000_000_000L);
  private final MarketDataQueue queue = new MarketDataQueue(instruments, relay, counters, CAPACITY);
  private final Trade trade = new Trade();
  private final BestBidAsk book = new BestBidAsk();

  @Test
  void handsEveryValueOverUnchangedAndInOrder() {
    queue.onTrade(
        trade
            .instrument(ETHUSDT)
            .tradeId(42)
            .price(345678, -2)
            .quantity(15, -3)
            .aggressorSide(AggressorSide.SELL)
            .exchangeTimestamp(1_000)
            .receiveTimestamp(2_000));
    queue.onBestBidAsk(
        book.instrument(BTCUSDT)
            .bookUpdateId(7)
            .bid(6421050, -2, 120, -5)
            .ask(6421051, -2, 3, -5)
            .exchangeTimestamp(3_000)
            .receiveTimestamp(4_000));
    queue.onTrade(trade.instrument(BTCUSDT).tradeId(43).aggressorSide(AggressorSide.BUY));

    assertThat(publisher.trades).as("nothing published before the agent drains").isEmpty();
    assertThat(queue.drain(10)).isEqualTo(3);

    assertThat(publisher.trades)
        .containsExactly(
            new RecordingPublisher.Trade(
                ETHUSDT, 42, 345_678_000_000L, 1_500_000L, 1_000, 2_000, AggressorSide.SELL),
            new RecordingPublisher.Trade(
                BTCUSDT, 43, 345_678_000_000L, 1_500_000L, 1_000, 2_000, AggressorSide.BUY));
    assertThat(publisher.bestBidAsks)
        .containsExactly(
            new RecordingPublisher.BestBidAsk(
                BTCUSDT,
                7,
                6_421_050_000_000L,
                120_000L,
                6_421_051_000_000L,
                3_000L,
                3_000,
                4_000));
  }

  @Test
  void drainsAtMostTheLimit() {
    for (int i = 1; i <= 5; i++) {
      queue.onTrade(validTrade(i));
    }

    assertThat(queue.drain(3)).isEqualTo(3);
    assertThat(queue.drain(3)).isEqualTo(2);
    assertThat(queue.drain(3)).isZero();
    assertThat(publisher.trades)
        .extracting(RecordingPublisher.Trade::tradeId)
        .containsExactly(1L, 2L, 3L, 4L, 5L);
  }

  @Test
  void rejectsInstrumentsTheGatewayDoesNotRelay() {
    queue.onTrade(validTrade(1).instrument(new Instrument("BINANCE", "SOLUSDT")));
    queue.onBestBidAsk(validBook(1).instrument(new Instrument("COINBASE", "BTCUSDT")));

    assertThat(queue.drain(10)).isZero();
    assertThat(counters.unknownInstruments()).isEqualTo(2);
  }

  @Test
  void rejectsTradesWithUnrepresentableValues() {
    queue.onTrade(validTrade(1).price(1, -9));
    queue.onTrade(validTrade(2).quantity(1, -9));

    assertThat(queue.drain(10)).isZero();
    assertThat(counters.unrepresentableValues()).isEqualTo(2);
  }

  @Test
  void rejectsTopOfBookUpdatesWithAnyUnrepresentableValue() {
    queue.onBestBidAsk(validBook(1).bid(1, -9, 0, 0));
    queue.onBestBidAsk(validBook(2).bid(0, 0, 1, -9));
    queue.onBestBidAsk(validBook(3).ask(1, -9, 0, 0));
    queue.onBestBidAsk(validBook(4).ask(0, 0, 1, -9));

    assertThat(queue.drain(10)).isZero();
    assertThat(counters.unrepresentableValues()).isEqualTo(4);
  }

  @Test
  void dropsAndCountsWhatDoesNotFitInsteadOfWaiting() {
    MarketDataQueue small = new MarketDataQueue(instruments, relay, counters, 1024);
    int sent = 100;
    for (int i = 1; i <= sent; i++) {
      small.onTrade(validTrade(i));
    }

    int drained = small.drain(sent);

    assertThat(counters.droppedEvents()).isPositive();
    assertThat(drained + counters.droppedEvents()).isEqualTo(sent);
    assertThat(publisher.trades)
        .as("the oldest are kept")
        .extracting(RecordingPublisher.Trade::tradeId)
        .startsWith(1L, 2L, 3L);
  }

  @Test
  void acceptsEventsFromSeveralThreadsAtOnce() throws InterruptedException {
    int threads = 4;
    int perThread = 200;
    CountDownLatch start = new CountDownLatch(1);
    List<Thread> producers = new ArrayList<>();
    for (int t = 0; t < threads; t++) {
      long first = (long) t * perThread;
      producers.add(
          Thread.ofPlatform()
              .start(
                  () -> {
                    // Each producer fills its own events, as each connection does
                    Trade own = new Trade();
                    awaitQuietly(start);
                    for (int i = 1; i <= perThread; i++) {
                      queue.onTrade(
                          own.instrument(BTCUSDT).tradeId(first + i).price(1, 0).quantity(1, 0));
                    }
                  }));
    }

    start.countDown();
    for (Thread producer : producers) {
      producer.join();
    }
    queue.drain(threads * perThread);

    assertThat(publisher.trades)
        .extracting(RecordingPublisher.Trade::tradeId)
        .doesNotHaveDuplicates()
        .hasSize(threads * perThread);
    assertThat(counters.droppedEvents()).isZero();
  }

  private Trade validTrade(long tradeId) {
    return trade.instrument(BTCUSDT).tradeId(tradeId).price(1, 0).quantity(1, 0);
  }

  private BestBidAsk validBook(long bookUpdateId) {
    return book.instrument(BTCUSDT).bookUpdateId(bookUpdateId).bid(1, 0, 1, 0).ask(2, 0, 1, 0);
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
