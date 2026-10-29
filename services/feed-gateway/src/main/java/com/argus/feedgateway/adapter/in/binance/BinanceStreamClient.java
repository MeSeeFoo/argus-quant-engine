package com.argus.feedgateway.adapter.in.binance;

import com.argus.feedgateway.application.port.in.IngestMarketData;
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
import org.agrona.concurrent.EpochNanoClock;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connection to Binance's SBE market data stream, with the JDK's WebSocket client.
 *
 * <p>The connection's callbacks run on the HTTP client's threads. Each binary frame, once complete,
 * is decoded there, with the time it was received, and its events are handed to the gateway through
 * {@link IngestMarketData}, which queues them and never waits. Each connection has its own decoder,
 * so two connections never share one.
 *
 * <p>The API key goes in the {@code X-MBX-APIKEY} header and is never logged. Pings are answered by
 * the JDK client, which sends a pong with the ping's payload.
 */
public final class BinanceStreamClient implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(BinanceStreamClient.class);
  private static final String API_KEY_HEADER = "X-MBX-APIKEY";
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(2);

  private final URI uri;
  private final String apiKey;
  private final Instruments instruments;
  private final IngestMarketData ingest;
  private final BinanceCounters counters;
  private final EpochNanoClock clock;
  private final int maxFrameLength;
  private final HttpClient httpClient = HttpClient.newHttpClient();
  private CompletableFuture<WebSocket> connection =
      CompletableFuture.failedFuture(new IllegalStateException("not connected yet"));
  private volatile @Nullable WebSocket webSocket;

  /**
   * Creates a client; nothing is opened until {@link #connect}.
   *
   * @param uri the stream to read, see {@link #streamUri}
   * @param maxFrameLength bytes of the largest frame accepted; a longer one is dropped and counted
   */
  public BinanceStreamClient(
      URI uri,
      String apiKey,
      Instruments instruments,
      IngestMarketData ingest,
      BinanceCounters counters,
      EpochNanoClock clock,
      int maxFrameLength) {
    if (apiKey.isBlank()) {
      throw new IllegalArgumentException(
          "A Binance API key is required: set BINANCE_API_KEY (see the feed gateway README)");
    }
    this.uri = uri;
    this.apiKey = apiKey;
    this.instruments = instruments;
    this.ingest = ingest;
    this.counters = counters;
    this.clock = clock;
    this.maxFrameLength = maxFrameLength;
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

  /**
   * Callbacks of one connection: the JDK calls them one at a time, so the connection's decoder is
   * never used by two threads at once. A new connection gets a new listener, so a frame cut short
   * by a lost connection never leaks into the next one.
   */
  private final class Listener implements WebSocket.Listener {

    private static final int MAX_TEXT_LENGTH = 4096;

    private final BinanceStreamDecoder decoder =
        new BinanceStreamDecoder(instruments, ingest, counters);
    private final UnsafeBuffer view = new UnsafeBuffer(0, 0);
    // A frame that arrives in several parts is put back together here, up to the largest frame
    // accepted; a longer one is dropped once complete.
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
        if (data.remaining() > maxFrameLength) {
          counters.droppedFrame();
        } else {
          view.wrap(data, data.position(), data.remaining());
          decode(view, data.remaining());
        }
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
            decode(parts, partsLength);
          }
          partsLength = 0;
          partsTooLong = false;
        }
      }
      webSocket.request(1);
      return null;
    }

    private void decode(DirectBuffer frame, int length) {
      try {
        decoder.onFrame(frame, 0, length, receiveTimestamp);
      } catch (RuntimeException e) {
        // A frame the decoder could not handle must not close the connection
        counters.unsupportedMessage();
        LOG.warn("Could not decode a frame from Binance", e);
      }
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
