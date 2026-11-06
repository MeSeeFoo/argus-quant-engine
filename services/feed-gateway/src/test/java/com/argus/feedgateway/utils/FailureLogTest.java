package com.argus.feedgateway.utils;

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

class FailureLogTest {

  private static final Logger LOG = LoggerFactory.getLogger(FailureLogTest.class);
  private final Exception failure = new IllegalStateException("transport down");

  @Test
  void logsTheFirstFailureAndSkipsTheRestOfTheInterval() {
    var log = new FailureLog(LOG, Duration.ofHours(1));

    assertThat(log.log("send failed", 1, failure)).as("the first one").isTrue();
    assertThat(log.log("send failed", 2, failure)).as("within the interval").isFalse();
    assertThat(log.log("send failed", 3, failure)).isFalse();
  }

  @Test
  void logsAgainOnceTheIntervalHasPassed() {
    var log = new FailureLog(LOG, Duration.ZERO);

    assertThat(log.log("send failed", 1, failure)).isTrue();
    assertThat(log.log("send failed", 2, failure)).isTrue();
  }

  @Test
  void logsOncePerIntervalWhateverTheThreads() throws Exception {
    var log = new FailureLog(LOG, Duration.ofHours(1));
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
                    if (log.log("send failed", j, failure)) {
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
        .isThrownBy(() -> new FailureLog(LOG, Duration.ofSeconds(-1)));
  }
}
