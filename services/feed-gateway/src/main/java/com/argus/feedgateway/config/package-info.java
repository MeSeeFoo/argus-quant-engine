/**
 * What is generic to the gateway: its instruments, the use case and the agent that runs it. Each
 * adapter brings its own configuration. Spring only builds the objects and starts the agent; the
 * hot path never runs through beans or proxies (ADR 0009, ADR 0010).
 */
package com.argus.feedgateway.config;
