package com.argus.modelengine.application;

import static com.argus.modelengine.application.TestMarketData.trade;
import static com.argus.modelengine.domain.marketdata.MarketEventType.TRADE;
import static org.assertj.core.api.Assertions.assertThat;

import com.argus.modelengine.domain.feed.Deduplicator;
import com.argus.modelengine.domain.feed.SequenceGapDetector;
import com.argus.modelengine.domain.instrument.Instrument;
import com.argus.modelengine.domain.instrument.Instruments;
import com.argus.modelengine.domain.model.DeployedModel;
import com.argus.modelengine.domain.model.Model;
import com.argus.modelengine.domain.model.ModelConfiguration;
import com.argus.modelengine.domain.model.ModelInputs;
import com.argus.modelengine.domain.window.WindowDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** What a full ring drops, with each strategy: the reader never waits for the evaluator. */
@ExtendWith(OutputCaptureExtension.class)
class BackpressureTest {

  private static final int SESSION = 1;
  private static final int RING_SIZE = 4;
  private static final Instrument SAN = Instrument.parse("EUREX:SAN");

  private final Instruments instruments = new Instruments(List.of(SAN));
  private final TradeIdModel model = new TradeIdModel();
  private final MarketDataCounters counters = new MarketDataCounters();
  private long nextId = 1;

  @Test
  void dropNewestKeepsWhatTheRingHoldsAndDropsWhatDoesNotFit() throws Exception {
    MarketDataService reader = reader(BackpressureStrategy.DROP_NEWEST);
    Evaluator evaluator = evaluator(reader);

    publishTrades(reader, 6);
    evaluator.doWork();

    assertThat(model.tradeIds).containsExactly(1L, 2L, 3L, 4L);
    assertThat(counters.droppedEvents()).isEqualTo(2);
    assertThat(counters.skippedEvents()).isZero();
  }

  @Test
  void theEvaluatorLogsWhatTheRingLost(CapturedOutput output) throws Exception {
    MarketDataService reader = reader(BackpressureStrategy.DROP_OLDEST);
    Evaluator evaluator = evaluator(reader);

    publishTrades(reader, 6);
    evaluator.doWork();
    evaluator.doWork();

    assertThat(output)
        .contains("events are lost with DROP_OLDEST")
        .contains("(" + (2 + RING_SIZE) + " so far");
  }

  @Test
  void dropOldestSkipsTheBacklogSoFreshDataFlowsAtOnce() throws Exception {
    MarketDataService reader = reader(BackpressureStrategy.DROP_OLDEST);
    Evaluator evaluator = evaluator(reader);

    publishTrades(reader, 6);
    assertThat(evaluator.doWork()).isZero();
    publishTrades(reader, 2);
    evaluator.doWork();

    assertThat(model.tradeIds).containsExactly(7L, 8L);
    assertThat(counters.droppedEvents()).isEqualTo(2);
    assertThat(counters.skippedEvents()).isEqualTo(RING_SIZE);
  }

  @Test
  void dropOldestSkipsOnlyWhatWasPublishedBeforeTheEvaluatorReacted() throws Exception {
    MarketDataService reader = reader(BackpressureStrategy.DROP_OLDEST);
    Evaluator evaluator = evaluator(reader);

    publishTrades(reader, 2);
    evaluator.doWork();
    publishTrades(reader, 5);
    evaluator.doWork();
    publishTrades(reader, 1);
    evaluator.doWork();

    assertThat(model.tradeIds).containsExactly(1L, 2L, 8L);
    assertThat(counters.droppedEvents()).isEqualTo(1);
    assertThat(counters.skippedEvents()).isEqualTo(RING_SIZE);
  }

  @Test
  void aRingThatNeverFillsLosesNothing() throws Exception {
    MarketDataService reader = reader(BackpressureStrategy.DROP_OLDEST);
    Evaluator evaluator = evaluator(reader);

    for (int i = 0; i < 3; i++) {
      publishTrades(reader, RING_SIZE);
      evaluator.doWork();
    }

    assertThat(model.tradeIds).hasSize(3 * RING_SIZE);
    assertThat(counters.droppedEvents()).isZero();
    assertThat(counters.skippedEvents()).isZero();
  }

  private MarketDataService reader(BackpressureStrategy strategy) {
    return new MarketDataService(
        new MarketDataEventChecker(
            new Deduplicator(1), new Deduplicator(1), new SequenceGapDetector(), counters),
        RING_SIZE,
        strategy,
        counters);
  }

  private Evaluator evaluator(MarketDataService reader) {
    DeployedModel deployed = DeployedModel.deploy(model, Map.of("trades", SAN), instruments);
    return new Evaluator(
        reader.newPoller(), reader.ringOverflowMechanism(), counters, 1, List.of(deployed));
  }

  private void publishTrades(MarketDataService reader, int count) {
    for (int i = 0; i < count; i++) {
      reader.onTrade(SESSION, trade(0, nextId, nextId));
      nextId++;
    }
  }

  /** Writes down the id of every trade it is run with. */
  private static final class TradeIdModel implements Model {

    final List<Long> tradeIds = new ArrayList<>();
    private final ModelConfiguration configuration =
        ModelConfiguration.builder().input("trades", TRADE, WindowDefinition.sliding(1)).build();

    @Override
    public ModelConfiguration modelConfiguration() {
      return configuration;
    }

    @Override
    public void evaluate(ModelInputs inputs) {
      tradeIds.add(inputs.trades("trades").events()[0].tradeId());
    }
  }
}
