package com.argus.modelengine.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.argus.modelengine.domain.marketdata.AggressorSide;
import com.argus.modelengine.domain.marketdata.MarketEventType;
import com.argus.modelengine.domain.marketdata.Trade;
import org.junit.jupiter.api.Test;

class MarketDataSlotTest {

  private final MarketDataSlot slot = new MarketDataSlot();

  @Test
  void holdsACopyOfATrade() {
    slot.setTrade(
        new TestMarketData.SampleTrade(3, 41, 11, 12, 1000, 100, 200, AggressorSide.SELL));

    assertThat(slot.kind()).isEqualTo(MarketEventType.TRADE);
    Trade trade = slot.trade();
    assertThat(trade.instrumentId()).isEqualTo(3);
    assertThat(trade.sequence()).isEqualTo(41);
    assertThat(trade.exchangeTimestamp()).isEqualTo(11);
    assertThat(trade.receiveTimestamp()).isEqualTo(12);
    assertThat(trade.tradeId()).isEqualTo(1000);
    assertThat(trade.priceMantissa()).isEqualTo(100);
    assertThat(trade.quantityMantissa()).isEqualTo(200);
    assertThat(trade.aggressorSide()).isEqualTo(AggressorSide.SELL);
  }

  @Test
  void holdsACopyOfATopOfBookUpdate() {
    slot.setBestBidAsk(new TestMarketData.SampleBestBidAsk(2, 42, 21, 22, 77, 100, 1, 101, 5));

    assertThat(slot.kind()).isEqualTo(MarketEventType.BEST_BID_ASK);
    assertThat(slot.bestBidAsk().instrumentId()).isEqualTo(2);
    assertThat(slot.bestBidAsk().sequence()).isEqualTo(42);
    assertThat(slot.bestBidAsk().exchangeTimestamp()).isEqualTo(21);
    assertThat(slot.bestBidAsk().receiveTimestamp()).isEqualTo(22);
    assertThat(slot.bestBidAsk().bookUpdateId()).isEqualTo(77);
    assertThat(slot.bestBidAsk().bidPriceMantissa()).isEqualTo(100);
    assertThat(slot.bestBidAsk().bidQuantityMantissa()).isEqualTo(1);
    assertThat(slot.bestBidAsk().askPriceMantissa()).isEqualTo(101);
    assertThat(slot.bestBidAsk().askQuantityMantissa()).isEqualTo(5);
  }

  @Test
  void aReusedSlotSwitchesKindAndKeepsTheSameObjects() {
    Trade trade = slot.trade();
    slot.setTrade(TestMarketData.trade(0, 1, 1000));

    slot.setBestBidAsk(TestMarketData.bestBidAsk(1, 2));
    slot.setTrade(TestMarketData.trade(0, 3, 1001));

    assertThat(slot.kind()).isEqualTo(MarketEventType.TRADE);
    assertThat(slot.trade()).isSameAs(trade);
    assertThat(slot.trade().tradeId()).isEqualTo(1001);
    assertThat(slot.bestBidAsk().sequence()).isEqualTo(2);
  }
}
