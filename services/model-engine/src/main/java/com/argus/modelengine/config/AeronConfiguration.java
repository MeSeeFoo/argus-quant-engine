package com.argus.modelengine.config;

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The JVM's embedded Media Driver and Aeron client (ADR 0021). They live here, not in an adapter,
 * because the engine's inbound and outbound Aeron adapters share them and adapters never depend on
 * each other (ADR 0009). Each adapter owns its channels and streams, and only injects the client.
 * Beans are closed in reverse order on shutdown, after the agents have stopped: client, driver.
 */
@Configuration(proxyBeanMethods = false)
class AeronConfiguration {

  /**
   * One driver per JVM, in a directory of its own that is deleted on start and on shutdown. Its
   * threads back off when idle instead of spinning: threads cannot be pinned on Fargate.
   *
   * @return the running driver
   */
  @Bean(destroyMethod = "close")
  MediaDriver mediaDriver() {
    return MediaDriver.launchEmbedded(
        new MediaDriver.Context()
            .threadingMode(ThreadingMode.SHARED)
            .sharedIdleStrategy(new BackoffIdleStrategy())
            .dirDeleteOnStart(true)
            .dirDeleteOnShutdown(true));
  }

  /**
   * The Aeron client, connected to the embedded driver.
   *
   * @param mediaDriver the embedded driver
   * @return the connected client
   */
  @Bean(destroyMethod = "close")
  Aeron aeron(MediaDriver mediaDriver) {
    return Aeron.connect(new Aeron.Context().aeronDirectoryName(mediaDriver.aeronDirectoryName()));
  }
}
