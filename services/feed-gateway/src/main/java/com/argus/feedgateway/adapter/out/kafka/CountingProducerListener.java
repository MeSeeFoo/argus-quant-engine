package com.argus.feedgateway.adapter.out.kafka;

import com.argus.feedgateway.utils.FailureLog;
import java.time.Duration;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.ProducerListener;

/**
 * Counts what Kafka acknowledged and what it failed to send. Called on the Kafka producer's I/O
 * thread, so it only touches the thread-safe counters.
 *
 * <p>Generic because Spring Boot injects it into its template as a {@code ProducerListener<Object,
 * Object>}: a bean declared with the template's own types would not match.
 *
 * @param <K> the record's key type
 * @param <V> the record's value type
 */
final class CountingProducerListener<K, V> implements ProducerListener<K, V> {

  private static final Logger LOG = LoggerFactory.getLogger(CountingProducerListener.class);
  // While Kafka is down every record fails: the counters count them, the log samples them
  private static final Duration FAILURE_LOG_INTERVAL = Duration.ofSeconds(10);

  private final KafkaCounters counters;
  private final FailureLog failureLog = new FailureLog(LOG, FAILURE_LOG_INTERVAL);

  /**
   * Creates a listener that writes {@code counters}.
   *
   * @param counters the adapter's counters
   */
  CountingProducerListener(KafkaCounters counters) {
    this.counters = counters;
  }

  @Override
  public void onSuccess(ProducerRecord<K, V> record, @Nullable RecordMetadata metadata) {
    if (record.value() instanceof com.argus.contracts.proto.market.trades.v1.Trade) {
      counters.countTrade();
    } else {
      counters.countBestBidAsk();
    }
  }

  @Override
  public void onError(
      @NonNull ProducerRecord<K, V> record,
      @Nullable RecordMetadata metadata,
      @NonNull Exception exception) {
    counters.countFailedSending();
    failureLog.log("Kafka did not accept a record", counters.failedSending(), exception);
  }
}
