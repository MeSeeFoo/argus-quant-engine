package com.argus.feedgateway.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What the gateway does, whatever adapters it runs with. Each adapter has its own properties under
 * {@code argus.feed-gateway.<adapter>}.
 *
 * @param instruments the shard's instruments as {@code VENUE:SYMBOL} ({@code BINANCE:BTCUSDT}),
 *     until the instrument catalogue arrives (ADR 0023)
 * @param heartbeatInterval a {@code Heartbeat} is published when nothing was published this long
 */
@ConfigurationProperties("argus.feed-gateway")
public record FeedGatewayProperties(List<String> instruments, Duration heartbeatInterval) {}
