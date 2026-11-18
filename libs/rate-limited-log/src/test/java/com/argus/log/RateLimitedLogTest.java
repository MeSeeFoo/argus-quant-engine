package com.argus.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RateLimitedLogTest {

  private static final Logger LOG = LoggerFactory.getLogger(RateLimitedLogTest.class);
  private final Exception failure = new IllegalStateException("transport down");

  @Test
  void logsTheFirstFailureAndSkipsTheRestOfTheInterval() {
    var log = new RateLimitedLog(LOG, Duration.ofHours(1));

    assertThat(log.error("send failed", 1, failure)).as("the first one").isTrue();
    assertThat(log.error("send failed", 2, failure)).as("within the interval").isFalse();
    assertThat(log.error("send failed", 3, failure)).isFalse();
  }

  @Test
  void warningsAndErrorsShareTheInterval() {
    var log = new RateLimitedLog(LOG, Duration.ofHours(1));

    assertThat(log.warn("ring full", 1)).isTrue();
    assertThat(log.warn("ring full", 2)).isFalse();
    assertThat(log.error("send failed", 1, failure)).isFalse();
  }

  @Test
  void logsAgainOnceTheIntervalHasPassed() {
    var log = new RateLimitedLog(LOG, Duration.ZERO);

    assertThat(log.error("send failed", 1, failure)).isTrue();
    assertThat(log.warn("ring full", 2)).isTrue();
  }

  @Test
  void logsOncePerIntervalWhateverTheThreads() throws Exception {
    var log = new RateLimitedLog(LOG, Duration.ofHours(1));
    var logged = new AtomicInteger();
    var start = new CountDownLatch(1);
    int threads = 8;

    try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
      Future<?>[] tasks = new Future<?>[threads];
      for (int i = 0; i < threads; i++) {
        tasks[i] =
            executor.submit(
                () -> {
                  start.await();
                  for (int j = 0; j < 1_000; j++) {
                    if (log.error("send failed", j, failure)) {
                      logged.incrementAndGet();
                    }
                  }
                  return null;
                });
      }
      start.countDown();
      for (Future<?> task : tasks) {
        task.get();
      }
    }

    assertThat(logged).hasValue(1);
  }

  @Test
  void rejectsANegativeInterval() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RateLimitedLog(LOG, Duration.ofSeconds(-1)));
  }
}
