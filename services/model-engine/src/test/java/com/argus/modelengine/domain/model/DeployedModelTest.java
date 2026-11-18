package com.argus.modelengine.domain.model;

import static com.argus.modelengine.domain.marketdata.MarketEventType.BEST_BID_ASK;
import static com.argus.modelengine.domain.marketdata.MarketEventType.TRADE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.argus.modelengine.domain.instrument.Instrument;
import com.argus.modelengine.domain.instrument.Instruments;
import com.argus.modelengine.domain.window.WindowDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeployedModelTest {

  private static final Instrument SAN = Instrument.parse("EUREX:SAN");
  private static final Instrument BBV = Instrument.parse("EUREX:BBV");
  private static final Instruments ENGINE_INSTRUMENTS = new Instruments(List.of(SAN, BBV));

  /** Model Y: every trade and every top-of-book update of one instrument. */
  private static final Model MODEL_Y =
      new Model() {
        @Override
        public ModelConfiguration modelConfiguration() {
          return ModelConfiguration.builder()
              .input("trades", TRADE, WindowDefinition.sliding(1))
              .input("quotes", BEST_BID_ASK, WindowDefinition.sliding(1))
              .build();
        }

        @Override
        public void evaluate(ModelInputs inputs) {}
      };

  @Test
  void joinsEachInputWithTheInstrumentTheDeploymentChose() {
    DeployedModel deployed =
        DeployedModel.deploy(MODEL_Y, Map.of("trades", BBV, "quotes", BBV), ENGINE_INSTRUMENTS);

    assertThat(deployed.model()).isSameAs(MODEL_Y);
    assertThat(deployed.inputs()).hasSize(2);
    assertThat(deployed.inputs().get(0).name()).isEqualTo("trades");
    assertThat(deployed.inputs().get(0).eventType()).isEqualTo(TRADE);
    assertThat(deployed.inputs().get(0).instrument()).isEqualTo(BBV);
    assertThat(deployed.inputs().get(0).instrumentId()).isEqualTo(1);
    assertThat(deployed.inputs().get(1).name()).isEqualTo("quotes");
    assertThat(deployed.inputs().get(1).eventType()).isEqualTo(BEST_BID_ASK);
  }

  @Test
  void theSameModelCanBeDeployedTwice() {
    DeployedModel onSan =
        DeployedModel.deploy(MODEL_Y, Map.of("trades", SAN, "quotes", SAN), ENGINE_INSTRUMENTS);
    DeployedModel onBbv =
        DeployedModel.deploy(MODEL_Y, Map.of("trades", BBV, "quotes", BBV), ENGINE_INSTRUMENTS);

    assertThat(onSan.inputs().get(0).instrumentId()).isZero();
    assertThat(onBbv.inputs().get(0).instrumentId()).isEqualTo(1);
  }

  @Test
  void itsInputsCannotChange() {
    InputDefinition definition = new InputDefinition("trades", TRADE, WindowDefinition.sliding(1));
    List<DeployedInput> inputs = new ArrayList<>(List.of(new DeployedInput(definition, SAN, 0)));
    DeployedModel deployed = new DeployedModel(MODEL_Y, inputs);

    inputs.clear();

    assertThat(deployed.inputs()).hasSize(1);
    assertThatThrownBy(() -> deployed.inputs().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void aDeployedModelNeedsAnInput() {
    assertThatThrownBy(() -> new DeployedModel(MODEL_Y, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aDeploymentMustGiveEveryInputAnInstrument() {
    assertThatThrownBy(
            () -> DeployedModel.deploy(MODEL_Y, Map.of("trades", SAN), ENGINE_INSTRUMENTS))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no instrument for input 'quotes'");
  }

  @Test
  void aDeploymentCannotNameInputsTheModelDoesNotDeclare() {
    Map<String, Instrument> instruments = Map.of("trades", SAN, "quotes", SAN, "volume", SAN);

    assertThatThrownBy(() -> DeployedModel.deploy(MODEL_Y, instruments, ENGINE_INSTRUMENTS))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("volume");
  }

  @Test
  void aDeploymentCanOnlyUseInstrumentsTheEngineHandles() {
    Map<String, Instrument> instruments =
        Map.of("trades", SAN, "quotes", Instrument.parse("BLOOM:TEF"));

    assertThatThrownBy(() -> DeployedModel.deploy(MODEL_Y, instruments, ENGINE_INSTRUMENTS))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("BLOOM:TEF");
  }

  @Test
  void aDeployedInputNeedsAValidInstrumentId() {
    InputDefinition definition = new InputDefinition("trades", TRADE, WindowDefinition.sliding(1));

    assertThatThrownBy(() -> new DeployedInput(definition, SAN, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
