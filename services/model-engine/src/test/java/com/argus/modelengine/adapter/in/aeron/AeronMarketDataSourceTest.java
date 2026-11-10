package com.argus.modelengine.adapter.in.aeron;

import static org.assertj.core.api.Assertions.assertThat;

import com.argus.contracts.sbe.BestBidAskEncoder;
import com.argus.contracts.sbe.HeartbeatEncoder;
import com.argus.contracts.sbe.MessageHeaderEncoder;
import com.argus.contracts.sbe.Side;
import com.argus.contracts.sbe.TradeDecoder;
import com.argus.contracts.sbe.TradeEncoder;
import com.argus.modelengine.application.port.in.IngestMarketData;
import com.argus.modelengine.domain.instrument.Instrument;
import com.argus.modelengine.domain.instrument.Instruments;
import com.argus.modelengine.domain.marketdata.AggressorSide;
import com.argus.modelengine.domain.marketdata.BestBidAsk;
import com.argus.modelengine.domain.marketdata.Trade;
import io.aeron.Aeron;
import io.aeron.ExclusivePublication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.agrona.CloseHelper;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Reads messages published over a real embedded Media Driver. */
class AeronMarketDataSourceTest {

  private static final String CHANNEL = "aeron:ipc";
  private static final int STREAM_ID = 1001;

  private final Instruments instruments =
      new Instruments(
          List.of(new Instrument("BINANCE", "BTCUSDT"), new Instrument("BINANCE", "ETHUSDT")));
  private final RecordingIngest ingest = new RecordingIngest();
  private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
  private final MessageHeaderEncoder header = new MessageHeaderEncoder();

  private MediaDriver driver;
  private Aeron aeron;
  private ExclusivePublication publication;
  private Subscription subscription;
  private AeronMarketDataSource source;

  @BeforeEach
  void connect() {
    driver =
        MediaDriver.launchEmbedded(
            new MediaDriver.Context()
                .threadingMode(ThreadingMode.SHARED)
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true));
    aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(driver.aeronDirectoryName()));
    publication = aeron.addExclusivePublication(CHANNEL, STREAM_ID);
    subscription = aeron.addSubscription(CHANNEL, STREAM_ID);
    awaitTrue(subscription::isConnected);
    source = new AeronMarketDataSource(subscription, instruments, ingest);
  }

  @AfterEach
  void disconnect() {
    CloseHelper.closeAll(subscription, publication, aeron, driver);
  }

  @Test
  void handsOverATrade() {
    publishTrade("ETHUSDT", 7, Side.SELL);

    readMessages(1);

    assertThat(ingest.seen)
        .containsExactly(
            "trade session="
                + publication.sessionId()
                + " instrument=1 sequence=7 id=1000 price=100 quantity=200"
                + " exchange=11 receive=12 side=SELL");
  }

  @Test
  void handsOverATopOfBookUpdate() {
    BestBidAskEncoder encoder = new BestBidAskEncoder();
    encoder
        .wrapAndApplyHeader(buffer, 0, header)
        .sequence(8)
        .bookUpdateId(77)
        .exchangeTimestamp(21)
        .receiveTimestamp(22)
        .venue((CharSequence) "BINANCE")
        .symbol((CharSequence) "BTCUSDT");
    encoder.bidPrice().mantissa(100);
    encoder.bidQuantity().mantissa(1);
    encoder.askPrice().mantissa(101);
    encoder.askQuantity().mantissa(2);
    publish(MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());

    readMessages(1);

    assertThat(ingest.seen)
        .containsExactly(
            "book session="
                + publication.sessionId()
                + " instrument=0 sequence=8 id=77 bid=100x1 ask=101x2 exchange=21 receive=22");
  }

  @Test
  void handsOverAHeartbeat() {
    HeartbeatEncoder encoder = new HeartbeatEncoder();
    encoder.wrapAndApplyHeader(buffer, 0, header).sequence(9).timestamp(33);
    publish(MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());

    readMessages(1);

    assertThat(ingest.seen).containsExactly("heartbeat session=" + publication.sessionId() + " 9");
  }

  @Test
  void anUnknownInstrumentIsCountedAndItsSequenceReported() {
    publishTrade("SOLUSDT", 7, Side.BUY);

    readMessages(1);

    assertThat(ingest.seen).containsExactly("ignored session=" + publication.sessionId() + " 7");
    assertThat(source.counters().unknownInstruments()).isEqualTo(1);
  }

  @Test
  void aTradeWithoutAValidSideIsCountedAndItsSequenceReported() {
    publishTrade("BTCUSDT", 7, Side.NULL_VAL);

    readMessages(1);

    assertThat(ingest.seen).containsExactly("ignored session=" + publication.sessionId() + " 7");
    assertThat(source.counters().malformed()).isEqualTo(1);
  }

  @Test
  void countsATemplateItDoesNotRead() {
    header
        .wrap(buffer, 0)
        .blockLength(0)
        .templateId(77)
        .schemaId(TradeDecoder.SCHEMA_ID)
        .version(0);
    publish(MessageHeaderEncoder.ENCODED_LENGTH);

    readMessages(1);

    assertThat(ingest.seen).isEmpty();
    assertThat(source.counters().unsupported()).isEqualTo(1);
  }

  @Test
  void countsAnotherSchema() {
    header
        .wrap(buffer, 0)
        .blockLength(0)
        .templateId(TradeDecoder.TEMPLATE_ID)
        .schemaId(99)
        .version(0);
    publish(MessageHeaderEncoder.ENCODED_LENGTH);

    readMessages(1);

    assertThat(ingest.seen).isEmpty();
    assertThat(source.counters().unsupported()).isEqualTo(1);
  }

  @Test
  void neverReadsMoreThanTheLimitAndLeavesTheRestInAeron() {
    publishTrade("BTCUSDT", 1, Side.BUY);
    publishTrade("BTCUSDT", 2, Side.BUY);
    publishTrade("BTCUSDT", 3, Side.BUY);

    assertThat(source.poll(0)).isZero();
    assertThat(ingest.seen).isEmpty();

    assertThat(source.poll(2)).isEqualTo(2);
    assertThat(ingest.seen).hasSize(2);

    assertThat(source.poll(2)).isEqualTo(1);
    assertThat(ingest.seen).hasSize(3);
  }

  private void publishTrade(String symbol, long sequence, Side side) {
    TradeEncoder encoder = new TradeEncoder();
    encoder
        .wrapAndApplyHeader(buffer, 0, header)
        .sequence(sequence)
        .tradeId(1000)
        .exchangeTimestamp(11)
        .receiveTimestamp(12)
        .aggressorSide(side)
        .venue((CharSequence) "BINANCE")
        .symbol((CharSequence) symbol);
    encoder.price().mantissa(100);
    encoder.quantity().mantissa(200);
    publish(MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
  }

  private void publish(int length) {
    awaitTrue(() -> publication.offer(buffer, 0, length) > 0);
  }

  private void readMessages(int count) {
    int[] read = {0};
    awaitTrue(
        () -> {
          read[0] += source.poll(count - read[0]);
          return read[0] == count;
        });
  }

  private static void awaitTrue(BooleanSupplier condition) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (!condition.getAsBoolean()) {
      assertThat(System.nanoTime()).as("timed out").isLessThan(deadline);
      Thread.onSpinWait();
    }
  }

  /** Writes down each call as text, because the events are valid only during the call. */
  private static final class RecordingIngest implements IngestMarketData {

    final List<String> seen = new ArrayList<>();
    private AggressorSide lastSide = AggressorSide.BUY;

    @Override
    public void onTrade(int session, Trade trade) {
      lastSide = trade.aggressorSide();
      seen.add(
          "trade session="
              + session
              + " instrument="
              + trade.instrumentId()
              + " sequence="
              + trade.sequence()
              + " id="
              + trade.tradeId()
              + " price="
              + trade.price()
              + " quantity="
              + trade.quantity()
              + " exchange="
              + trade.exchangeTimestamp()
              + " receive="
              + trade.receiveTimestamp()
              + " side="
              + lastSide);
    }

    @Override
    public void onBestBidAsk(int session, BestBidAsk book) {
      seen.add(
          "book session="
              + session
              + " instrument="
              + book.instrumentId()
              + " sequence="
              + book.sequence()
              + " id="
              + book.bookUpdateId()
              + " bid="
              + book.bidPrice()
              + "x"
              + book.bidQuantity()
              + " ask="
              + book.askPrice()
              + "x"
              + book.askQuantity()
              + " exchange="
              + book.exchangeTimestamp()
              + " receive="
              + book.receiveTimestamp());
    }

    @Override
    public void onIgnored(int session, long sequence) {
      seen.add("ignored session=" + session + " " + sequence);
    }

    @Override
    public void onHeartbeat(int session, long lastPublished) {
      seen.add("heartbeat session=" + session + " " + lastPublished);
    }
  }
}
