package com.argus.feedgateway.adapter.out;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.ToDoubleFunction;

/**
 * Registers {@code argus.feed.messages.published} and {@code argus.feed.messages.dropped}, both
 * tagged with the {@code transport}, so every output adapter reports what it published and dropped
 * the same way. Every meter of one name must have the same tag keys: Prometheus rejects the rest.
 */
public final class PublicationMetrics {

  /** Name of the counter of messages handed to a transport. */
  public static final String PUBLISHED = "argus.feed.messages.published";

  /** Name of the counter of messages dropped instead of blocking. */
  public static final String DROPPED = "argus.feed.messages.dropped";

  private PublicationMetrics() {}

  /**
   * Registers the trades and top-of-book updates counted in {@code counters}.
   *
   * @param registry where to register them
   * @param counters the adapter's counters
   * @param transport the value of the {@code transport} tag
   */
  public static void register(
      MeterRegistry registry, PublicationCounters counters, String transport) {
    published(registry, counters, transport, "trade", PublicationCounters::trades);
    published(registry, counters, transport, "best_bid_ask", PublicationCounters::bestBidAsks);
  }

  /**
   * Registers one more message type that only {@code transport} publishes.
   *
   * @param <T> the type of the counters
   * @param registry where to register it
   * @param counters the adapter's counters
   * @param transport the value of the {@code transport} tag
   * @param type the value of the {@code type} tag
   * @param count reads the count from {@code counters}
   */
  public static <T> void published(
      MeterRegistry registry,
      T counters,
      String transport,
      String type,
      ToDoubleFunction<T> count) {
    FunctionCounter.builder(PUBLISHED, counters, count)
        .description("Messages handed to a transport")
        .tag("type", type)
        .tag("transport", transport)
        .register(registry);
  }

  /**
   * Registers the messages {@code transport} dropped for {@code reason}.
   *
   * @param <T> the type of the counters
   * @param registry where to register it
   * @param counters the adapter's counters
   * @param transport the value of the {@code transport} tag
   * @param reason the value of the {@code reason} tag
   * @param count reads the count from {@code counters}
   */
  public static <T> void dropped(
      MeterRegistry registry,
      T counters,
      String transport,
      String reason,
      ToDoubleFunction<T> count) {
    FunctionCounter.builder(DROPPED, counters, count)
        .description("Messages dropped instead of blocking")
        .tag("reason", reason)
        .tag("transport", transport)
        .register(registry);
  }
}
