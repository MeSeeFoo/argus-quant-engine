package com.argus.modelengine.config;

import com.argus.modelengine.application.BackpressureStrategy;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What the engine does, whatever its adapters (ADR 0009).
 *
 * @param instruments the instruments the engine handles, as {@code VENUE:SYMBOL}, until the
 *     instrument catalogue arrives (ADR 0023)
 * @param ringSize slots of the Disruptor ring between the reader and the evaluator, a power of two
 *     (ADR 0029)
 * @param backpressure what the engine drops when that ring is full (ADR 0029)
 */
@ConfigurationProperties("argus.model-engine")
public record ModelEngineProperties(
    List<String> instruments, int ringSize, BackpressureStrategy backpressure) {}
