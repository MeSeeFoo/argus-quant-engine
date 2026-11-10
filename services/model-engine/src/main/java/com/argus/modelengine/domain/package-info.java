/**
 * The engine's domain, one package per concept: instruments, market data, the feed's checks, stores
 * and windows, and models. Plain Java, with no allocation on the hot path. No Spring, Kafka or
 * Aeron here (ADR 0009).
 */
package com.argus.modelengine.domain;
