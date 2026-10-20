package com.argus.feedgateway.adapter.out.aeron;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.argus.contracts.sbe.BestBidAskDecoder;
import com.argus.contracts.sbe.HeartbeatDecoder;
import com.argus.contracts.sbe.MessageHeaderDecoder;
import com.argus.contracts.sbe.Side;
import com.argus.contracts.sbe.TradeDecoder;
import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.Instrument;
import io.aeron.Aeron;
import io.aeron.ExclusivePublication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.DatagramSocket;
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
  private static final long HEARTBEAT_INTERVAL = TimeUnit.SECONDS.toNanos(1);
  private static final Instrument BTCUSDT = new Instrument("BINANCE", "BTCUSDT");

  private final PublicationCounters counters = new PublicationCounters();
  private final List<UnsafeBuffer> received = new ArrayList<>();
  private final MessageHeaderDecoder header = new MessageHeaderDecoder();

  private MediaDriver driver;
  private Aeron aeron;
  private String controlEndpoint;
  private ExclusivePublication publication;
  private AeronMarketDataPublisher publisher;

  @BeforeEach
  void startDriver() throws IOException {
    driver =
        MediaDriver.launchEmbedded(
            new MediaDriver.Context()
                .threadingMode(ThreadingMode.SHARED)
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true));
    aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(driver.aeronDirectoryName()));
    controlEndpoint = "localhost:" + freeUdpPort();
    publication =
        aeron.addExclusivePublication(
            "aeron:udp?control=" + controlEndpoint + "|control-mode=dynamic", STREAM_ID);
    publisher = new AeronMarketDataPublisher(publication, counters, HEARTBEAT_INTERVAL);
  }

  @AfterEach
  void stopDriver() {
    CloseHelper.closeAll(publication, aeron, driver);
  }

  @Test
  void publishesTradesAndTopOfBookWithOneSequence() {
    Subscription subscription = subscribe();

    publisher.publishTrade(
        BTCUSDT, 1001, 6_421_050_000_000L, 12_000_000, 11, 12, AggressorSide.SELL);
    publisher.publishBestBidAsk(
        BTCUSDT, 77, 6_421_050_000_000L, 3_000, 6_421_051_000_000L, 120_000, 21, 22);
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
  void sendsHeartbeatsOnlyWhileIdle() {
    Subscription subscription = subscribe();
    long start = 1_000_000_000L;

    assertThat(publisher.heartbeatIfIdle(start)).as("idle since start").isEqualTo(1);
    assertThat(publisher.heartbeatIfIdle(start + HEARTBEAT_INTERVAL - 1)).isZero();
    publisher.publishTrade(BTCUSDT, 1, 1, 1, 1, 1, AggressorSide.BUY);
    assertThat(publisher.heartbeatIfIdle(start + HEARTBEAT_INTERVAL))
        .as("a trade was published in the interval")
        .isZero();
    assertThat(publisher.heartbeatIfIdle(start + 2 * HEARTBEAT_INTERVAL)).isEqualTo(1);
    assertThat(publisher.heartbeatIfIdle(start + 3 * HEARTBEAT_INTERVAL)).isEqualTo(1);
    receive(subscription, 4);

    HeartbeatDecoder heartbeat = new HeartbeatDecoder();
    header.wrap(received.get(0), 0);
    heartbeat.wrapAndApplyHeader(received.get(0), 0, header);
    assertThat(heartbeat.sequence()).as("nothing published yet").isZero();
    assertThat(heartbeat.timestamp()).isEqualTo(start);
    header.wrap(received.get(2), 0);
    heartbeat.wrapAndApplyHeader(received.get(2), 0, header);
    assertThat(heartbeat.sequence()).as("the last sequence published").isEqualTo(1);
    assertThat(heartbeat.timestamp()).isEqualTo(start + 2 * HEARTBEAT_INTERVAL);
    assertThat(counters.heartbeats()).isEqualTo(3);
  }

  @Test
  void dropsWithoutASubscriberAndLeavesAGapInTheSequence() {
    publisher.publishTrade(BTCUSDT, 1, 1, 1, 1, 1, AggressorSide.BUY);
    assertThat(counters.droppedNotConnected()).isEqualTo(1);
    assertThat(counters.trades()).isZero();

    Subscription subscription = subscribe();
    publisher.publishTrade(BTCUSDT, 2, 1, 1, 1, 1, AggressorSide.BUY);
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
    assertThat(
            counters.trades()
                + counters.bestBidAsks()
                + counters.droppedBackPressured()
                + counters.droppedOther())
        .as("every message either sent or dropped")
        .isEqualTo(4L * messages);
    assertThat(allocated).isLessThan(1_024);
  }

  @Test
  void requiresAPositiveHeartbeatInterval() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new AeronMarketDataPublisher(publication, counters, 0));
  }

  private void publishTrades(int count) {
    for (int i = 0; i < count; i++) {
      publisher.publishTrade(BTCUSDT, i, 1, 1, 1, 1, AggressorSide.BUY);
      publisher.publishBestBidAsk(BTCUSDT, i, 1, 1, 2, 1, 1, 1);
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

  private static int freeUdpPort() throws IOException {
    try (DatagramSocket socket = new DatagramSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
