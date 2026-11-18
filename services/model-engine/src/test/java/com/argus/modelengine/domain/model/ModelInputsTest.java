package com.argus.modelengine.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.argus.modelengine.domain.marketdata.AggressorSide;
import com.argus.modelengine.domain.marketdata.BestBidAsk;
import com.argus.modelengine.domain.marketdata.Trade;
import com.argus.modelengine.domain.window.EventWindow;
import com.argus.modelengine.domain.window.Window;
import com.argus.modelengine.domain.window.WindowDefinition;
import org.junit.jupiter.api.Test;

class ModelInputsTest {

  private final EventWindow<Trade> tradeWindow =
      EventWindow.ofTrades("trades", WindowDefinition.sliding(1));
  private final EventWindow<BestBidAsk> quoteWindow =
      EventWindow.ofBestBidAsks("quotes", WindowDefinition.sliding(1));
  private final MutableModelInputs inputs =
      new MutableModelInputs(new Window<?>[] {tradeWindow.view(), quoteWindow.view()});

  @Test
  void aModelReadsEachInputByName() {
    tradeWindow.append(trade(1000));

    Window<Trade> trades = inputs.trades("trades");
    assertThat(trades.events()).hasSize(1);
    assertThat(trades.events()[0].tradeId()).isEqualTo(1000);
    assertThat(inputs.get("trades")).isSameAs(trades);
    assertThat(inputs.bestBidAsks("quotes").events()).isEmpty();
  }

  @Test
  void theWindowsAreAlwaysUpToDate() {
    Window<Trade> trades = inputs.trades("trades");

    tradeWindow.append(trade(1));
    tradeWindow.append(trade(2));

    assertThat(inputs.trades("trades")).isSameAs(trades);
    assertThat(trades.events()[0].tradeId()).isEqualTo(2);
  }

  @Test
  void saysWhichInputFired() {
    inputs.fired(0);
    assertThat(inputs.fired()).isEqualTo("trades");

    inputs.fired(1);
    assertThat(inputs.fired()).isEqualTo("quotes");
  }

  @Test
  void rejectsAnInputOfTheOtherKind() {
    assertThatThrownBy(() -> inputs.trades("quotes"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("BEST_BID_ASK");
    assertThatThrownBy(() -> inputs.bestBidAsks("trades"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("TRADE");
  }

  @Test
  void rejectsAnUnknownInputName() {
    assertThatThrownBy(() -> inputs.get("volume"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("'volume'");
  }

  private static Trade trade(long tradeId) {
    return new SampleTrade(0, tradeId, 1, 2, tradeId, 100, 200, AggressorSide.BUY);
  }

  /** A trade with the values a test needs; a record, so its getters implement the interface. */
  private record SampleTrade(
      int instrumentId,
      long sequence,
      long exchangeTimestamp,
      long receiveTimestamp,
      long tradeId,
      long priceMantissa,
      long quantityMantissa,
      AggressorSide aggressorSide)
      implements Trade {}
}
