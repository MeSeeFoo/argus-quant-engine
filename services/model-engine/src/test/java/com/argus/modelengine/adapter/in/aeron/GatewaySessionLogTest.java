package com.argus.modelengine.adapter.in.aeron;

import static org.assertj.core.api.Assertions.assertThat;

import io.aeron.Aeron;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import java.util.concurrent.TimeUnit;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Logs a real Aeron image, from an embedded Media Driver over IPC. Not a mock: mocking {@link
 * Image} with Mockito's inline mock maker rewrites the class for the whole JVM, so every poll of
 * the other tests would go through Mockito and allocate.
 */
@ExtendWith(OutputCaptureExtension.class)
class GatewaySessionLogTest {

  private static final String CHANNEL = "aeron:ipc";
  private static final int STREAM_ID = 1001;

  private MediaDriver driver;
  private Aeron aeron;
  private ExclusivePublication publication;
  private Subscription subscription;
  private Image image;

  @BeforeEach
  void connect() {
    driver =
        MediaDriver.launchEmbedded(
            new MediaDriver.Context()
                .threadingMode(ThreadingMode.SHARED)
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true));
    aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(driver.aeronDirectoryName()));
    publication = aeron.addExclusivePublication(CHANNEL, STREAM_ID);
    subscription = aeron.addSubscription(CHANNEL, STREAM_ID);
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (!subscription.isConnected()) {
      assertThat(System.nanoTime()).as("timed out").isLessThan(deadline);
      Thread.onSpinWait();
    }
    image = subscription.imageAtIndex(0);
  }

  @AfterEach
  void disconnect() {
    CloseHelper.closeAll(subscription, publication, aeron, driver);
  }

  @Test
  void logsASessionThatConnects(CapturedOutput output) {
    GatewaySessionLog.onAvailable(image);

    assertThat(output)
        .contains("Gateway session " + publication.sessionId() + " connected from " + CHANNEL);
  }

  @Test
  void logsASessionThatLeaves(CapturedOutput output) {
    GatewaySessionLog.onUnavailable(image);

    assertThat(output)
        .contains("Gateway session " + publication.sessionId() + " from " + CHANNEL + " is gone");
  }
}
