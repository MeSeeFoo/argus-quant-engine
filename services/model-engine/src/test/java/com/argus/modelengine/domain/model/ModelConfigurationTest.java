package com.argus.modelengine.domain.model;

import static com.argus.modelengine.domain.marketdata.MarketEventType.BEST_BID_ASK;
import static com.argus.modelengine.domain.marketdata.MarketEventType.TRADE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.argus.modelengine.domain.window.WindowDefinition;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelConfigurationTest {

  @Test
  void aModelOnTheTopOfBookInSlicesOf100() {
    ModelConfiguration z =
        ModelConfiguration.builder()
            .input("quotes", BEST_BID_ASK, WindowDefinition.tumbling(100))
            .build();

    assertThat(z.inputs())
        .containsExactly(
            new InputDefinition("quotes", BEST_BID_ASK, WindowDefinition.tumbling(100)));
  }

  @Test
  void keepsTheInputsInDeclarationOrder() {
    ModelConfiguration spread =
        ModelConfiguration.builder()
            .input("leg2_quotes", BEST_BID_ASK, WindowDefinition.sliding(1))
            .input("leg1_quotes", BEST_BID_ASK, WindowDefinition.sliding(1))
            .input("leg2_trades", TRADE, WindowDefinition.sliding(10))
            .build();

    assertThat(spread.inputs())
        .extracting(InputDefinition::name)
        .containsExactly("leg2_quotes", "leg1_quotes", "leg2_trades");
  }

  @Test
  void isImmutable() {
    ModelConfiguration.Builder builder =
        ModelConfiguration.builder().input("trades", TRADE, WindowDefinition.sliding(1));
    ModelConfiguration configuration = builder.build();

    builder.input("quotes", BEST_BID_ASK, WindowDefinition.sliding(1));

    assertThat(configuration.inputs()).hasSize(1);
    assertThatThrownBy(() -> configuration.inputs().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void copiesTheListItIsGiven() {
    List<InputDefinition> inputs = new ArrayList<>();
    inputs.add(new InputDefinition("trades", TRADE, WindowDefinition.sliding(1)));
    ModelConfiguration configuration = new ModelConfiguration(inputs);

    inputs.clear();

    assertThat(configuration.inputs()).hasSize(1);
  }

  @Test
  void needsAtLeastOneInput() {
    assertThatThrownBy(() -> ModelConfiguration.builder().build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one input");
  }

  @Test
  void rejectsTwoInputsWithTheSameName() {
    ModelConfiguration.Builder builder =
        ModelConfiguration.builder()
            .input("quotes", BEST_BID_ASK, WindowDefinition.sliding(1))
            .input("quotes", TRADE, WindowDefinition.sliding(1));

    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("'quotes'");
  }

  @Test
  void rejectsNamesThatAreNotLowercaseIdentifiers() {
    WindowDefinition window = WindowDefinition.sliding(1);

    assertThatThrownBy(() -> new InputDefinition("", TRADE, window))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new InputDefinition("Trades", TRADE, window))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new InputDefinition("1trades", TRADE, window))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new InputDefinition("my-trades", TRADE, window))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("input name");
    assertThat(new InputDefinition("leg_2", TRADE, window).name()).isEqualTo("leg_2");
  }
}
