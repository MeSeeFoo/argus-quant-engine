package com.argus.modelengine.adapter.in.aeron;

import io.aeron.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs each gateway session that connects to the engine's subscription or leaves it: with an active
 * and a passive gateway, this is how a failover shows (ADR 0022). Aeron calls it on its client's
 * conductor thread, never on the reader's, so it may log freely.
 */
final class GatewaySessionLog {

  private static final Logger LOG = LoggerFactory.getLogger(GatewaySessionLog.class);

  /** Not instantiable: its methods are handed to Aeron as handlers. */
  private GatewaySessionLog() {}

  /**
   * A gateway session started sending to the engine.
   *
   * @param image the session's image
   */
  static void onAvailable(Image image) {
    LOG.info("Gateway session {} connected from {}", image.sessionId(), image.sourceIdentity());
  }

  /**
   * A gateway session stopped sending to the engine: it closed, or went silent for too long.
   *
   * @param image the session's image
   */
  static void onUnavailable(Image image) {
    LOG.warn("Gateway session {} from {} is gone", image.sessionId(), image.sourceIdentity());
  }
}
