package com.argus.feedgateway.adapter.in.binance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.argus.feedgateway.adapter.in.binance.BinanceFrames.Trade;
import com.argus.feedgateway.application.MarketDataCounters;
import com.argus.feedgateway.application.MarketDataNormaliser;
import com.argus.feedgateway.application.RecordingPublisher;
import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.Instrument;
import com.argus.feedgateway.domain.Instruments;
import jakarta.websocket.Session;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The client against a local WebSocket server that stands in for Binance: no key, no network. */
class BinanceStreamClientTest {

  private static final long RECEIVED = 1_760_000_000_123_456_789L;
  private static final int RING_CAPACITY = 64 * 1024;

  private final Instruments instruments =
      new Instruments(
          List.of(new Instrument("BINANCE", "BTCUSDT"), new Instrument("BINANCE", "ETHUSDT")));
  private final RecordingPublisher publisher = new RecordingPublisher();
  private final MarketDataCounters counters = new MarketDataCounters();
  private final BinanceFrames frames = new BinanceFrames();
  private final UnsafeBuffer frame = new UnsafeBuffer(new byte[256]);

  private TestWebSocketServer server;
  private BinanceStreamClient client;

  @BeforeEach
  void startServer() throws Exception {
    server = new TestWebSocketServer();
    client = connect(RING_CAPACITY);
  }

  @AfterEach
  void stop() throws Exception {
    client.close();
    server.close();
  }

  @Test
  void subscribesToEveryInstrumentWithTheApiKey() throws Exception {
    server.accept();

    assertThat(server.handshakeQuery())
        .isEqualTo("streams=btcusdt@trade/btcusdt@bestBidAsk/ethusdt@trade/ethusdt@bestBidAsk");
    assertThat(server.handshakeHeaders().get("X-MBX-APIKEY")).containsExactly("test-key");
  }

  @Test
  void decodesBinaryFramesOnThePollingThread() throws Exception {
    Session session = server.accept();

    int length = frames.trades(frame, 0, "BTCUSDT", 1, 2, -2, -8, new Trade(7, 6421050, 1, true));
    session.getBasicRemote().sendBinary(ByteBuffer.wrap(frame.byteArray(), 0, length));
    awaitTrue(() -> client.poll(10) > 0 || !publisher.trades.isEmpty());

    assertThat(publisher.trades)
        .containsExactly(
            new RecordingPublisher.Trade(
                instruments.get(0), 7, 6_421_050_000_000L, 1, 2_000, RECEIVED, AggressorSide.SELL));
  }

  @Test
  void reassemblesFramesSentInParts() throws Exception {
    Session session = server.accept();

    int length = frames.bestBidAsk(frame, 0, "ETHUSDT", 3, 9, -2, -4, 250010, 5, 250011, 7);
    int half = length / 2;
    session.getBasicRemote().sendBinary(ByteBuffer.wrap(frame.byteArray(), 0, half), false);
    session
        .getBasicRemote()
        .sendBinary(ByteBuffer.wrap(frame.byteArray(), half, length - half), true);
    awaitTrue(() -> client.poll(10) > 0 || !publisher.bestBidAsks.isEmpty());

    assertThat(publisher.bestBidAsks)
        .extracting(RecordingPublisher.BestBidAsk::bookUpdateId)
        .containsExactly(9L);
    assertThat(counters.unsupportedMessages()).isZero();
  }

  @Test
  void answersPingsWithTheirPayload() throws Exception {
    Session session = server.accept();

    session
        .getBasicRemote()
        .sendPing(ByteBuffer.wrap("still there?".getBytes(StandardCharsets.UTF_8)));

    assertThat(StandardCharsets.UTF_8.decode(server.nextPong()).toString())
        .isEqualTo("still there?");
  }

  @Test
  void ignoresTextFrames() throws Exception {
    Session session = server.accept();

    session.getBasicRemote().sendText("{\"e\":\"serverShutdown\",\"E\":1770123456789}");
    int length = frames.trades(frame, 0, "BTCUSDT", 1, 2, -2, -8, new Trade(8, 1, 1, false));
    session.getBasicRemote().sendBinary(ByteBuffer.wrap(frame.byteArray(), 0, length));
    awaitTrue(() -> client.poll(10) > 0 || !publisher.trades.isEmpty());

    assertThat(publisher.trades).extracting(RecordingPublisher.Trade::tradeId).containsExactly(8L);
    assertThat(counters.unsupportedMessages()).isZero();
  }

  @Test
  void dropsFramesWhenTheAgentFallsBehind() throws Exception {
    server.accept(); // the connection opened before each test
    client.close();
    // 1 KiB of ring holds about ten frames, and one message is at most 128 bytes
    client = connect(1024);
    Session session = server.accept();

    int sent = 50;
    for (int i = 1; i <= sent; i++) {
      int length = frames.trades(frame, 0, "BTCUSDT", 1, 2, -2, -8, new Trade(i, 1, 1, false));
      session.getBasicRemote().sendBinary(ByteBuffer.wrap(frame.byteArray(), 0, length));
    }
    session.getBasicRemote().sendBinary(ByteBuffer.wrap(new byte[200])); // larger than a message
    awaitTrue(
        () -> {
          client.poll(100);
          return publisher.trades.size() + counters.droppedFrames() == sent + 1;
        });

    assertThat(counters.droppedFrames()).isPositive();
  }

  @Test
  void reportsAConnectionThatCannotBeOpened() {
    client.close();
    // Nothing listens on port 1
    client = connect(URI.create("ws://localhost:1"), RING_CAPACITY);

    assertThatThrownBy(() -> client.connection().get(10, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class);
  }

  @Test
  void closesTheConnection() throws Exception {
    Session session = server.accept();
    client.connection().get(10, TimeUnit.SECONDS);

    client.close();

    awaitTrue(() -> !session.isOpen());
  }

  @Test
  void requiresAnApiKey() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new BinanceStreamClient(
                    server.uri(), " ", decoder(), counters, () -> RECEIVED, RING_CAPACITY))
        .withMessageContaining("BINANCE_API_KEY");
  }

  @Test
  void buildsTheCombinedStreamAddress() {
    assertThat(
            BinanceStreamClient.streamUri(
                URI.create("wss://stream-sbe.binance.com:9443"),
                new Instruments(List.of(new Instrument("BINANCE", "BTCUSDT")))))
        .hasToString(
            "wss://stream-sbe.binance.com:9443/stream?streams=btcusdt@trade/btcusdt@bestBidAsk");
  }

  private BinanceStreamClient connect(int ringCapacity) {
    return connect(server.uri(), ringCapacity);
  }

  private BinanceStreamClient connect(URI base, int ringCapacity) {
    BinanceStreamClient connected =
        new BinanceStreamClient(
            BinanceStreamClient.streamUri(base, instruments),
            "test-key",
            decoder(),
            counters,
            () -> RECEIVED,
            ringCapacity);
    connected.connect();
    return connected;
  }

  private BinanceStreamDecoder decoder() {
    return new BinanceStreamDecoder(
        instruments, new MarketDataNormaliser(instruments, publisher, counters), counters);
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
