package com.argus.feedgateway.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The gateway's fast-path stream: one per shard (ADR 0023).
 *
 * @param channel Aeron channel of the publication. UDP with a dynamic multi-destination-cast
 *     control endpoint, where subscribers register themselves (ADR 0021).
 * @param streamId stream id of the shard
 * @param heartbeatInterval a {@code Heartbeat} is sent when the stream was idle this long
 */
@ConfigurationProperties("argus.feed-gateway.aeron")
public record AeronProperties(String channel, int streamId, Duration heartbeatInterval) {}
