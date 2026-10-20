package com.argus.feedgateway.adapter.out.aeron;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The gateway's fast-path stream: one per shard (ADR 0023).
 *
 * @param channel Aeron channel of the publication. UDP with a dynamic multi-destination-cast
 *     control endpoint, where subscribers register themselves (ADR 0021).
 * @param streamId stream id of the shard
 */
@ConfigurationProperties("argus.feed-gateway.aeron")
public record AeronProperties(String channel, int streamId) {}
