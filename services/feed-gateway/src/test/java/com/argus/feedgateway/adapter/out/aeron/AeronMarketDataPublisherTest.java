package com.argus.feedgateway.adapter.out.aeron;

import static org.assertj.core.api.Assertions.assertThat;

import com.argus.contracts.sbe.BestBidAskDecoder;
import com.argus.contracts.sbe.HeartbeatDecoder;
import com.argus.contracts.sbe.MessageHeaderDecoder;
import com.argus.contracts.sbe.Side;
import com.argus.contracts.sbe.TradeDecoder;
import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.BestBidAsk;
import com.argus.feedgateway.domain.Instrument;
import com.argus.feedgateway.domain.Trade;
import io.aeron.Aeron;
import io.aeron.ExclusivePublication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.agrona.CloseHelper;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Publishes over a real embedded Media Driver and reads the stream back with a subscriber. */
class AeronMarketDataPublisherTest {

  private static final int STREAM_ID = 1001;
  private static final Instrument BTCUSDT = new Instrument("BINANCE", "BTCUSDT");

  private final PublicationCounters counters = new PublicationCounters();
  private final List<UnsafeBuffer> received = new ArrayList<>();
  private final MessageHeaderDecoder header = new MessageHeaderDecoder();
  private final Trade reusedTrade = new Trade();
  private final BestBidAsk reusedBook = new BestBidAsk();

  private MediaDriver driver;
  private Aeron aeron;
  private String controlEndpoint;
  private ExclusivePublication publication;
  private AeronMarketDataPublisher publisher;

  @BeforeEach
  void startDriver() {
    driver =
        MediaDriver.launchEmbedded(
            new MediaDriver.Context()
                .threadingMode(ThreadingMode.SHARED)
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true));
    aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(driver.aeronDirectoryName()));
    open("");
  }

  /**
   * Opens the publication with a control endpoint on any free port, and remembers the address the
   * driver bound so subscribers can register with it.
   */
  private void open(String channelParameters) {
    CloseHelper.close(publication);
    publication =
        aeron.addExclusivePublication(
            "aeron:udp?control=localhost:0|control-mode=dynamic|fc=max" + channelParameters,
            STREAM_ID);
    awaitTrue(() -> !publication.localSocketAddresses().isEmpty());
    controlEndpoint = publication.localSocketAddresses().getFirst();
    publisher = new AeronMarketDataPublisher(publication, counters);
  }

  @AfterEach
  void stopDriver() {
    CloseHelper.closeAll(publication, aeron, driver);
  }

  @Test
  void publishesTradesAndTopOfBookWithOneSequence() {
    Subscription subscription = subscribe();

    publisher.publishTrade(
        trade(1001)
            .price(6_421_050_000_000L, -8)
            .quantity(12_000_000, -8)
            .exchangeTimestamp(11)
            .receiveTimestamp(12)
            .aggressorSide(AggressorSide.SELL));
    publisher.publishBestBidAsk(
        book(77)
            .bid(6_421_050_000_000L, -8, 3_000, -8)
            .ask(6_421_051_000_000L, -8, 120_000, -8)
            .exchangeTimestamp(21)
            .receiveTimestamp(22));
    receive(subscription, 2);

    TradeDecoder trade = new TradeDecoder();
    header.wrap(received.get(0), 0);
    trade.wrapAndApplyHeader(received.get(0), 0, header);
    assertThat(trade.sequence()).isEqualTo(1);
    assertThat(trade.tradeId()).isEqualTo(1001);
    assertThat(trade.price().mantissa()).isEqualTo(6_421_050_000_000L);
    assertThat(trade.quantity().mantissa()).isEqualTo(12_000_000);
    assertThat(trade.exchangeTimestamp()).isEqualTo(11);
    assertThat(trade.receiveTimestamp()).isEqualTo(12);
    assertThat(trade.aggressorSide()).isEqualTo(Side.SELL);
    assertThat(trade.venue()).isEqualTo("BINANCE");
    assertThat(trade.symbol()).isEqualTo("BTCUSDT");

    BestBidAskDecoder book = new BestBidAskDecoder();
    book.wrapAndApplyHeader(received.get(1), 0, header);
    assertThat(book.sequence()).isEqualTo(2);
    assertThat(book.bookUpdateId()).isEqualTo(77);
    assertThat(book.bidPrice().mantissa()).isEqualTo(6_421_050_000_000L);
    assertThat(book.bidQuantity().mantissa()).isEqualTo(3_000);
    assertThat(book.askPrice().mantissa()).isEqualTo(6_421_051_000_000L);
    assertThat(book.askQuantity().mantissa()).isEqualTo(120_000);
    assertThat(book.exchangeTimestamp()).isEqualTo(21);
    assertThat(book.receiveTimestamp()).isEqualTo(22);
    assertThat(book.venue()).isEqualTo("BINANCE");
    assertThat(book.symbol()).isEqualTo("BTCUSDT");

    assertThat(counters.trades()).isEqualTo(1);
    assertThat(counters.bestBidAsks()).isEqualTo(1);
  }

  @Test
  void heartbeatsCarryTheLastSequenceAndTheSenderClock() {
    Subscription subscription = subscribe();
    long epoch = 1_760_000_000_000_000_000L;

    publisher.publishHeartbeat(epoch);
    publisher.publishTrade(trade(1));
    publisher.publishHeartbeat(epoch + 7);
    receive(subscription, 3);

    assertThat(received)
        .extracting(message -> header.wrap(message, 0).templateId())
        .containsExactly(
            HeartbeatDecoder.TEMPLATE_ID, TradeDecoder.TEMPLATE_ID, HeartbeatDecoder.TEMPLATE_ID);
    HeartbeatDecoder heartbeat = new HeartbeatDecoder();
    header.wrap(received.get(0), 0);
    heartbeat.wrapAndApplyHeader(received.get(0), 0, header);
    assertThat(heartbeat.sequence()).as("nothing published yet").isZero();
    assertThat(heartbeat.timestamp()).isEqualTo(epoch);
    header.wrap(received.get(2), 0);
    heartbeat.wrapAndApplyHeader(received.get(2), 0, header);
    assertThat(heartbeat.sequence()).as("the last sequence published").isEqualTo(1);
    assertThat(heartbeat.timestamp()).isEqualTo(epoch + 7);
    assertThat(counters.heartbeats()).isEqualTo(2);
  }

  @Test
  void losesNothingWhenTermsRotate() {
    // 64 KiB terms hold 512 frames of 128 bytes: 1 500 trades rotate them about three times
    open("|term-length=64k");
    Subscription subscription = subscribe();
    int trades = 1_500;

    for (int i = 1; i <= trades; i++) {
      publisher.publishTrade(trade(i));
      receive(subscription, i);
    }

    TradeDecoder trade = new TradeDecoder();
    for (int i = 0; i < trades; i++) {
      header.wrap(received.get(i), 0);
      assertThat(trade.wrapAndApplyHeader(received.get(i), 0, header).sequence()).isEqualTo(i + 1L);
    }
    assertThat(counters.trades()).isEqualTo(trades);
    assertThat(counters.droppedOther()).isZero();
  }

  @Test
  void dropsWithoutASubscriberAndLeavesAGapInTheSequence() {
    publisher.publishTrade(trade(1));
    assertThat(counters.droppedNotConnected()).isEqualTo(1);
    assertThat(counters.trades()).isZero();

    Subscription subscription = subscribe();
    publisher.publishTrade(trade(2));
    receive(subscription, 1);

    TradeDecoder trade = new TradeDecoder();
    header.wrap(received.get(0), 0);
    trade.wrapAndApplyHeader(received.get(0), 0, header);
    assertThat(trade.tradeId()).isEqualTo(2);
    assertThat(trade.sequence()).as("sequence 1 was dropped").isEqualTo(2);
  }

  @Test
  void neverBlocksNorAllocatesWhenSubscribersFallBehind() {
    subscribe(); // never polled: the publication fills up
    var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    int messages = 200_000;

    publishTrades(messages); // warm-up, until every path, back-pressure included, has run
    long before = threads.getCurrentThreadAllocatedBytes();
    publishTrades(messages);
    long allocated = threads.getCurrentThreadAllocatedBytes() - before;

    assertThat(counters.droppedBackPressured()).isPositive();
    assertThat(counters.droppedOther()).as("term rotations are retried").isZero();
    assertThat(counters.trades() + counters.bestBidAsks() + counters.droppedBackPressured())
        .as("every message either sent or dropped")
        .isEqualTo(4L * messages);
    assertThat(allocated).isLessThan(1_024);
  }

  /** A trade of BTCUSDT, in a reused object as the gateway passes them. */
  private Trade trade(long tradeId) {
    return reusedTrade
        .instrument(BTCUSDT)
        .tradeId(tradeId)
        .price(1, 0)
        .quantity(1, 0)
        .aggressorSide(AggressorSide.BUY)
        .exchangeTimestamp(1)
        .receiveTimestamp(1);
  }

  private BestBidAsk book(long bookUpdateId) {
    return reusedBook
        .instrument(BTCUSDT)
        .bookUpdateId(bookUpdateId)
        .bid(1, 0, 1, 0)
        .ask(2, 0, 1, 0)
        .exchangeTimestamp(1)
        .receiveTimestamp(1);
  }

  private void publishTrades(int count) {
    for (int i = 0; i < count; i++) {
      publisher.publishTrade(trade(i));
      publisher.publishBestBidAsk(book(i));
    }
  }

  private Subscription subscribe() {
    Subscription subscription =
        aeron.addSubscription(
            "aeron:udp?endpoint=localhost:0|control=" + controlEndpoint + "|control-mode=dynamic",
            STREAM_ID);
    // Both ends: data offered before the subscriber's image exists would be lost
    awaitTrue(() -> publication.isConnected() && subscription.isConnected());
    return subscription;
  }

  private void receive(Subscription subscription, int count) {
    awaitTrue(
        () -> {
          subscription.poll(
              (buffer, offset, length, ignored) -> {
                byte[] copy = new byte[length];
                buffer.getBytes(offset, copy);
                received.add(new UnsafeBuffer(copy));
              },
              10);
          return received.size() >= count;
        });
    assertThat(received).hasSize(count);
  }

  private static void awaitTrue(BooleanSupplier condition) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("timed out");
      }
      Thread.onSpinWait();
    }
  }
}
