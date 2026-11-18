package com.argus.modelengine.adapter.in.aeron;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The gateway's fast-path stream the engine subscribes to (ADR 0021, ADR 0023).
 *
 * @param channel Aeron channel of the subscription: UDP, registered with the gateway's dynamic
 *     multi-destination-cast control endpoint
 * @param streamId stream id of the gateway's shard
 */
@ConfigurationProperties("argus.model-engine.aeron")
public record AeronInputProperties(String channel, int streamId) {}
