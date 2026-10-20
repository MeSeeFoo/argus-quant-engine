/**
 * Spring wiring. Spring only builds the agents and starts them on their own threads; the hot path
 * never runs through beans or proxies (ADR 0010).
 */
package com.argus.feedgateway.config;
