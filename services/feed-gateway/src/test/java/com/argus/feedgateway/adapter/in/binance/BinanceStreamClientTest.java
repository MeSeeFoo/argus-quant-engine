package com.argus.feedgateway.adapter.in.binance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.argus.feedgateway.adapter.in.binance.BinanceFrames.Trade;
import com.argus.feedgateway.application.MarketDataCounters;
import com.argus.feedgateway.application.MarketDataQueue;
import com.argus.feedgateway.application.MarketDataRelay;
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

/**
 * The client against a local WebSocket server that stands in for Binance: no key, no network. The
 * test thread plays the gateway agent: it drains the queue the client feeds.
 */
class BinanceStreamClientTest {

  private static final long RECEIVED = 1_760_000_000_123_456_789L;
  private static final int QUEUE_CAPACITY = 64 * 1024;
  private static final int MAX_FRAME_LENGTH = 64 * 1024;

  private final Instruments instruments =
      new Instruments(
          List.of(new Instrument("BINANCE", "BTCUSDT"), new Instrument("BINANCE", "ETHUSDT")));
  private final RecordingPublisher publisher = new RecordingPublisher();
  private final BinanceCounters counters = new BinanceCounters();
  private final MarketDataCounters relayCounters = new MarketDataCounters();
  private final BinanceFrames frames = new BinanceFrames();
  private final UnsafeBuffer frame = new UnsafeBuffer(new byte[256]);

  private TestWebSocketServer server;
  private MarketDataQueue queue;
  private BinanceStreamClient client;

  @BeforeEach
  void startServer() throws Exception {
    server = new TestWebSocketServer();
    queue = newQueue(QUEUE_CAPACITY);
    client = connect(MAX_FRAME_LENGTH);
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
  void decodesBinaryFramesIntoTheQueue() throws Exception {
    Session session = server.accept();

    int length = frames.trades(frame, 0, "BTCUSDT", 1, 2, -2, -8, new Trade(7, 6421050, 1, true));
    session.getBasicRemote().sendBinary(ByteBuffer.wrap(frame.byteArray(), 0, length));
    awaitTrue(() -> drained() && !publisher.trades.isEmpty());

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
    awaitTrue(() -> drained() && !publisher.bestBidAsks.isEmpty());

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
    awaitTrue(() -> drained() && !publisher.trades.isEmpty());

    assertThat(publisher.trades).extracting(RecordingPublisher.Trade::tradeId).containsExactly(8L);
    assertThat(counters.unsupportedMessages()).isZero();
  }

  @Test
  void dropsEventsWhenTheAgentFallsBehind() throws Exception {
    server.accept(); // the connection opened before each test
    client.close();
    // 1 KiB of queue holds about eighteen trades
    queue = newQueue(1024);
    client = connect(MAX_FRAME_LENGTH);
    Session session = server.accept();

    int sent = 50;
    for (int i = 1; i <= sent; i++) {
      int length = frames.trades(frame, 0, "BTCUSDT", 1, 2, -2, -8, new Trade(i, 1, 1, false));
      session.getBasicRemote().sendBinary(ByteBuffer.wrap(frame.byteArray(), 0, length));
    }
    // Nothing drains the queue yet, so it fills up
    awaitTrue(() -> relayCounters.droppedEvents() > 0);
    awaitTrue(
        () -> {
          queue.drain(100);
          return publisher.trades.size() + relayCounters.droppedEvents() == sent;
        });

    assertThat(publisher.trades).hasSizeLessThan(sent);
  }

  @Test
  void dropsFramesLargerThanAccepted() throws Exception {
    server.accept();
    client.close();
    client = connect(128);
    Session session = server.accept();

    session.getBasicRemote().sendBinary(ByteBuffer.wrap(new byte[200]));
    session.getBasicRemote().sendBinary(ByteBuffer.wrap(new byte[100]), false);
    session.getBasicRemote().sendBinary(ByteBuffer.wrap(new byte[100]), true);
    int length = frames.trades(frame, 0, "BTCUSDT", 1, 2, -2, -8, new Trade(3, 1, 1, false));
    session.getBasicRemote().sendBinary(ByteBuffer.wrap(frame.byteArray(), 0, length));
    awaitTrue(() -> drained() && !publisher.trades.isEmpty());

    assertThat(counters.droppedFrames()).as("one whole, one in parts").isEqualTo(2);
    assertThat(publisher.trades).extracting(RecordingPublisher.Trade::tradeId).containsExactly(3L);
  }

  @Test
  void startsEachConnectionWithoutTheRestOfAFrameCutShort() throws Exception {
    Session session = server.accept();
    int length = frames.trades(frame, 0, "BTCUSDT", 1, 2, -2, -8, new Trade(4, 1, 1, false));
    session.getBasicRemote().sendBinary(ByteBuffer.wrap(frame.byteArray(), 0, length / 2), false);
    session.close(); // the connection is lost halfway through the frame

    client.connect();
    Session reconnected = server.accept();
    reconnected.getBasicRemote().sendBinary(ByteBuffer.wrap(frame.byteArray(), 0, length));
    awaitTrue(() -> drained() && !publisher.trades.isEmpty());

    assertThat(publisher.trades).extracting(RecordingPublisher.Trade::tradeId).containsExactly(4L);
    assertThat(counters.unsupportedMessages()).isZero();
  }

  @Test
  void keepsOnlyTheStartOfLongTextFrames() throws Exception {
    Session session = server.accept();

    session.getBasicRemote().sendText("x".repeat(10_000));
    int length = frames.trades(frame, 0, "BTCUSDT", 1, 2, -2, -8, new Trade(5, 1, 1, false));
    session.getBasicRemote().sendBinary(ByteBuffer.wrap(frame.byteArray(), 0, length));
    awaitTrue(() -> drained() && !publisher.trades.isEmpty());

    assertThat(publisher.trades).extracting(RecordingPublisher.Trade::tradeId).containsExactly(5L);
  }

  @Test
  void reportsAConnectionThatCannotBeOpened() {
    client.close();
    // Nothing listens on port 1
    client = connect(URI.create("ws://localhost:1"), MAX_FRAME_LENGTH);

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
                    server.uri(),
                    " ",
                    instruments,
                    queue,
                    counters,
                    () -> RECEIVED,
                    MAX_FRAME_LENGTH))
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

  /** Drains the queue as the agent would; always true, to chain with the condition awaited. */
  private boolean drained() {
    queue.drain(10);
    return true;
  }

  private MarketDataQueue newQueue(int capacity) {
    return new MarketDataQueue(
        instruments,
        new MarketDataRelay(instruments, publisher, relayCounters, 1_000_000_000L),
        relayCounters,
        capacity);
  }

  /** Connects to the test server, feeding the current queue. */
  private BinanceStreamClient connect(int maxFrameLength) {
    return connect(server.uri(), maxFrameLength);
  }

  private BinanceStreamClient connect(URI base, int maxFrameLength) {
    BinanceStreamClient connected =
        new BinanceStreamClient(
            BinanceStreamClient.streamUri(base, instruments),
            "test-key",
            instruments,
            queue,
            counters,
            () -> RECEIVED,
            maxFrameLength);
    connected.connect();
    return connected;
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
