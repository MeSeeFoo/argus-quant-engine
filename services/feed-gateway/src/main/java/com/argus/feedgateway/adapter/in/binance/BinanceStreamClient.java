package com.argus.feedgateway.adapter.in.binance;

import com.argus.feedgateway.application.MarketDataCounters;
import com.argus.feedgateway.application.MarketDataSource;
import com.argus.feedgateway.domain.Instruments;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.EpochNanoClock;
import org.agrona.concurrent.MessageHandler;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connection to Binance's SBE market data stream, with the JDK's WebSocket client.
 *
 * <p>The connection's callbacks run on the HTTP client's threads. They only copy each binary frame,
 * with the time it was received, into a pre-allocated ring buffer, and never wait: when the ring is
 * full the frame is dropped and counted. The gateway agent drains the ring with {@link #poll} and
 * decodes on its own thread, so decoding and publishing stay single-threaded and allocation-free.
 *
 * <p>The API key goes in the {@code X-MBX-APIKEY} header and is never logged. Pings are answered by
 * the JDK client, which sends a pong with the ping's payload.
 */
public final class BinanceStreamClient implements MarketDataSource, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(BinanceStreamClient.class);
  private static final String API_KEY_HEADER = "X-MBX-APIKEY";
  private static final int FRAME_MESSAGE = 1;
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(2);

  private final URI uri;
  private final String apiKey;
  private final BinanceStreamDecoder decoder;
  private final MarketDataCounters counters;
  private final EpochNanoClock clock;
  private final RingBuffer frames;
  private final MessageHandler frameHandler = this::onFrame;
  private final HttpClient httpClient = HttpClient.newHttpClient();
  private CompletableFuture<WebSocket> connection =
      CompletableFuture.failedFuture(new IllegalStateException("not connected yet"));
  private volatile @Nullable WebSocket webSocket;

  /**
   * Creates a client; nothing is opened until {@link #connect}.
   *
   * @param uri the stream to read, see {@link #streamUri}
   * @param ringCapacity bytes of the ring between the connection and the agent; a power of two
   */
  public BinanceStreamClient(
      URI uri,
      String apiKey,
      BinanceStreamDecoder decoder,
      MarketDataCounters counters,
      EpochNanoClock clock,
      int ringCapacity) {
    if (apiKey.isBlank()) {
      throw new IllegalArgumentException(
          "A Binance API key is required: set BINANCE_API_KEY (see the feed gateway README)");
    }
    this.uri = uri;
    this.apiKey = apiKey;
    this.decoder = decoder;
    this.counters = counters;
    this.clock = clock;
    this.frames =
        new ManyToOneRingBuffer(
            new UnsafeBuffer(
                ByteBuffer.allocateDirect(ringCapacity + RingBufferDescriptor.TRAILER_LENGTH)));
  }

  /**
   * The combined stream of trades and top-of-book updates of every instrument: {@code
   * <base>/stream?streams=btcusdt@trade/btcusdt@bestBidAsk}.
   */
  public static URI streamUri(URI base, Instruments instruments) {
    StringJoiner streams = new StringJoiner("/");
    for (int i = 0; i < instruments.size(); i++) {
      String symbol = instruments.get(i).symbol().toLowerCase(Locale.ROOT);
      streams.add(symbol + "@trade").add(symbol + "@bestBidAsk");
    }
    return URI.create(base + "/stream?streams=" + streams);
  }

  /** Opens the connection in the background; failures are logged. */
  public synchronized void connect() {
    connection =
        httpClient
            .newWebSocketBuilder()
            .header(API_KEY_HEADER, apiKey)
            .connectTimeout(CONNECT_TIMEOUT)
            .buildAsync(uri, new Listener())
            .whenComplete(
                (connected, error) -> {
                  if (error == null) {
                    webSocket = connected;
                    LOG.info("Connected to {}", uri);
                  } else {
                    LOG.error("Could not connect to {}", uri, error);
                  }
                });
  }

  /** The current connection attempt, completed when the connection is open. */
  public synchronized CompletableFuture<WebSocket> connection() {
    return connection;
  }

  @Override
  public int poll(int limit) {
    return frames.read(frameHandler, limit);
  }

  /** Closes the connection politely, then stops the HTTP client. */
  @Override
  public void close() {
    WebSocket current = webSocket;
    if (current != null) {
      try {
        current
            .sendClose(WebSocket.NORMAL_CLOSURE, "")
            .toCompletableFuture()
            .get(CLOSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (ExecutionException | TimeoutException e) {
        LOG.debug("Close handshake with {} did not complete", uri, e);
      }
      current.abort();
    }
    httpClient.shutdownNow();
  }

  private void onFrame(int messageType, MutableDirectBuffer buffer, int index, int length) {
    decoder.onFrame(buffer, index + Long.BYTES, length - Long.BYTES, buffer.getLong(index));
  }

  /** Copies a frame into the ring, after its receive time. Never waits. */
  private void enqueue(DirectBuffer frame, int offset, int length, long receiveTimestamp) {
    int messageLength = Long.BYTES + length;
    if (messageLength > frames.maxMsgLength()) {
      counters.droppedFrame();
      return;
    }
    int index = frames.tryClaim(FRAME_MESSAGE, messageLength);
    if (index < 0) {
      counters.droppedFrame();
      return;
    }
    MutableDirectBuffer ring = frames.buffer();
    ring.putLong(index, receiveTimestamp);
    ring.putBytes(index + Long.BYTES, frame, offset, length);
    frames.commit(index);
  }

  /**
   * Callbacks of one connection: the JDK calls them one at a time. A new connection gets a new
   * listener, so a frame cut short by a lost connection never leaks into the next one.
   */
  private final class Listener implements WebSocket.Listener {

    private static final int MAX_TEXT_LENGTH = 4096;

    private final UnsafeBuffer view = new UnsafeBuffer(0, 0);
    // A frame that arrives in several parts is put back together here, up to the largest frame
    // the ring accepts; a longer one is dropped once complete.
    private final int maxFrameLength = frames.maxMsgLength() - Long.BYTES;
    private final ExpandableArrayBuffer parts = new ExpandableArrayBuffer(4096);
    private int partsLength;
    private boolean partsTooLong;
    private long receiveTimestamp;
    private final StringBuilder text = new StringBuilder();

    @Override
    public void onOpen(WebSocket webSocket) {
      webSocket.request(1);
    }

    @Override
    public @Nullable CompletionStage<?> onBinary(
        WebSocket webSocket, ByteBuffer data, boolean last) {
      boolean firstPart = partsLength == 0 && !partsTooLong;
      if (firstPart) {
        receiveTimestamp = clock.nanoTime();
      }
      if (last && firstPart) {
        view.wrap(data, data.position(), data.remaining());
        enqueue(view, 0, data.remaining(), receiveTimestamp);
      } else {
        if (partsLength + data.remaining() > maxFrameLength) {
          partsTooLong = true;
        } else if (!partsTooLong) {
          parts.putBytes(partsLength, data, data.position(), data.remaining());
          partsLength += data.remaining();
        }
        if (last) {
          if (partsTooLong) {
            counters.droppedFrame();
          } else {
            enqueue(parts, 0, partsLength, receiveTimestamp);
          }
          partsLength = 0;
          partsTooLong = false;
        }
      }
      webSocket.request(1);
      return null;
    }

    /** Text frames are JSON: subscription responses and {@code serverShutdown} events. */
    @Override
    public @Nullable CompletionStage<?> onText(
        WebSocket webSocket, CharSequence data, boolean last) {
      if (text.length() < MAX_TEXT_LENGTH) {
        text.append(data, 0, Math.min(data.length(), MAX_TEXT_LENGTH - text.length()));
      }
      if (last) {
        LOG.info("Message from Binance: {}", text);
        text.setLength(0);
      }
      webSocket.request(1);
      return null;
    }

    @Override
    public @Nullable CompletionStage<?> onClose(WebSocket closed, int statusCode, String reason) {
      LOG.warn("Binance closed the connection: {} {}", statusCode, reason);
      webSocket = null;
      return null;
    }

    @Override
    public void onError(WebSocket failed, Throwable error) {
      LOG.error("Connection to Binance failed", error);
      webSocket = null;
    }
  }
}
