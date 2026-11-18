package com.argus.modelengine.application;

import static com.argus.modelengine.application.TestMarketData.trade;
import static com.argus.modelengine.domain.marketdata.MarketEventType.BEST_BID_ASK;
import static com.argus.modelengine.domain.marketdata.MarketEventType.TRADE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.argus.modelengine.application.TestMarketData.SampleBestBidAsk;
import com.argus.modelengine.domain.feed.Deduplicator;
import com.argus.modelengine.domain.feed.SequenceGapDetector;
import com.argus.modelengine.domain.instrument.Instrument;
import com.argus.modelengine.domain.instrument.Instruments;
import com.argus.modelengine.domain.marketdata.Trade;
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

@ExtendWith(OutputCaptureExtension.class)
class EvaluatorTest {

  private static final int SESSION = 1;
  private static final Instrument SAN = Instrument.parse("EUREX:SAN");
  private static final Instrument BBV = Instrument.parse("EUREX:BBV");

  private final Instruments instruments = new Instruments(List.of(SAN, BBV));
  private final MarketDataCounters counters = new MarketDataCounters();
  private final MarketDataService reader =
      new MarketDataService(
          new MarketDataEventChecker(
              new Deduplicator(2), new Deduplicator(2), new SequenceGapDetector(), counters),
          512,
          BackpressureStrategy.DROP_OLDEST,
          counters);
  private long nextSequence = 1;
  private long nextId = 1;

  /** Model Y: every trade and every top-of-book update of one instrument. */
  private final ModelConfiguration modelY =
      ModelConfiguration.builder()
          .input("trades", TRADE, WindowDefinition.sliding(1))
          .input("quotes", BEST_BID_ASK, WindowDefinition.sliding(1))
          .build();

  /** Model Z: the top of book in slices of 3 updates. */
  private final ModelConfiguration modelZ =
      ModelConfiguration.builder()
          .input("quotes", BEST_BID_ASK, WindowDefinition.tumbling(3))
          .build();

  @Test
  void runsTheModelOfAWindowThatFiresWithAllItsInputs() throws Exception {
    RecordingModel y = new RecordingModel(modelY);
    Evaluator evaluator = evaluator(deploy(y, Map.of("trades", SAN, "quotes", SAN)));

    publishTrade(0);
    evaluator.doWork();

    assertThat(y.fired).containsExactly("trades");
    assertThat(y.lastTradeIds).containsExactly(1L);
    assertThat(y.quotesSeen).containsExactly(0);
  }

  @Test
  void theOtherInputsAreReadAsTheyAreAtThatMoment() throws Exception {
    RecordingModel y = new RecordingModel(modelY);
    Evaluator evaluator = evaluator(deploy(y, Map.of("trades", SAN, "quotes", SAN)));

    publishBestBidAsk(0);
    publishTrade(0);
    evaluator.doWork();

    assertThat(y.fired).containsExactly("quotes", "trades");
    assertThat(y.quotesSeen).containsExactly(1, 1);
  }

  @Test
  void eventsOfOtherInstrumentsDoNotReachTheWindows() throws Exception {
    RecordingModel y = new RecordingModel(modelY);
    Evaluator evaluator = evaluator(deploy(y, Map.of("trades", BBV, "quotes", BBV)));

    publishTrade(0);
    publishBestBidAsk(0);
    evaluator.doWork();

    assertThat(y.fired).isEmpty();
  }

  @Test
  void aTumblingWindowRunsItsModelOncePerSlice() throws Exception {
    RecordingModel z = new RecordingModel(modelZ);
    Evaluator evaluator = evaluator(deploy(z, Map.of("quotes", SAN)));

    for (int i = 0; i < 7; i++) {
      publishBestBidAsk(0);
    }
    evaluator.doWork();

    assertThat(z.fired).containsExactly("quotes", "quotes");
  }

  @Test
  void handlesNoMoreThanTheBatchLimitPerDutyCycle() throws Exception {
    Evaluator evaluator = evaluator();
    for (int i = 0; i < Evaluator.BATCH_LIMIT + 44; i++) {
      publishTrade(0);
    }

    assertThat(evaluator.doWork()).isEqualTo(Evaluator.BATCH_LIMIT);
    assertThat(evaluator.doWork()).isEqualTo(44);
    assertThat(evaluator.doWork()).isZero();
  }

  @Test
  void aFailingModelIsLoggedWithWhatItThrew(CapturedOutput output) throws Exception {
    RecordingModel failing = new RecordingModel(modelY);
    failing.fail = true;
    Evaluator evaluator = evaluator(deploy(failing, Map.of("trades", SAN, "quotes", SAN)));

    publishTrade(0);
    publishTrade(0);
    evaluator.doWork();

    assertThat(output)
        .contains("Model " + RecordingModel.class.getName() + " threw while evaluating")
        .contains("(1 so far")
        .contains("deliberate failure")
        .doesNotContain("(2 so far");
  }

  @Test
  void aFailingModelIsCountedAndTheOthersGoOn() throws Exception {
    RecordingModel failing = new RecordingModel(modelY);
    failing.fail = true;
    RecordingModel y = new RecordingModel(modelY);
    Evaluator evaluator =
        evaluator(
            deploy(failing, Map.of("trades", SAN, "quotes", SAN)),
            deploy(y, Map.of("trades", SAN, "quotes", SAN)));

    publishTrade(0);
    publishTrade(0);

    assertThat(evaluator.doWork()).isEqualTo(2);
    assertThat(counters.failedEvaluations()).isEqualTo(2);
    assertThat(y.fired).hasSize(2);
  }

  @Test
  void rejectsADeploymentOnAnInstrumentItDoesNotHandle() {
    DeployedModel onBbv = deploy(new RecordingModel(modelZ), Map.of("quotes", BBV));

    assertThatThrownBy(
            () ->
                new Evaluator(
                    reader.newPoller(),
                    reader.ringOverflowMechanism(),
                    counters,
                    1,
                    List.of(onBbv)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("'quotes'");
  }

  private Evaluator evaluator(DeployedModel... models) {
    return new Evaluator(
        reader.newPoller(),
        reader.ringOverflowMechanism(),
        counters,
        instruments.size(),
        List.of(models));
  }

  private DeployedModel deploy(Model model, Map<String, Instrument> bindings) {
    return DeployedModel.deploy(model, bindings, instruments);
  }

  private void publishTrade(int instrumentId) {
    reader.onTrade(SESSION, trade(instrumentId, nextSequence++, nextId++));
  }

  private void publishBestBidAsk(int instrumentId) {
    reader.onBestBidAsk(
        SESSION,
        new SampleBestBidAsk(instrumentId, nextSequence++, 21, 22, nextId++, 100, 1, 101, 2));
  }

  /** Writes down what it saw at each evaluation. */
  private static final class RecordingModel implements Model {

    final List<String> fired = new ArrayList<>();
    final List<Long> lastTradeIds = new ArrayList<>();
    final List<Integer> quotesSeen = new ArrayList<>();
    boolean fail = false;
    private final ModelConfiguration configuration;

    RecordingModel(ModelConfiguration configuration) {
      this.configuration = configuration;
    }

    @Override
    public ModelConfiguration modelConfiguration() {
      return configuration;
    }

    @Override
    public void evaluate(ModelInputs inputs) {
      if (fail) {
        throw new IllegalStateException("deliberate failure");
      }
      fired.add(inputs.fired());
      if (inputs.fired().equals("trades")) {
        Trade[] trades = inputs.trades("trades").events();
        lastTradeIds.add(trades[trades.length - 1].tradeId());
      }
      // Every model of these tests has a "quotes" input
      quotesSeen.add(inputs.bestBidAsks("quotes").events().length);
    }
  }
}
