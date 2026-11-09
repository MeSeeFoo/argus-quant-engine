package com.argus.modelengine.application;

import static com.argus.modelengine.application.TestMarketData.bestBidAsk;
import static com.argus.modelengine.application.TestMarketData.trade;
import static org.assertj.core.api.Assertions.assertThat;

import com.argus.modelengine.domain.BestBidAsk;
import com.argus.modelengine.domain.EventWindow;
import com.argus.modelengine.domain.Trade;
import org.junit.jupiter.api.Test;

class MarketEventStoresTest {

  private static final MarketEventStores.Limits LIMITS = new MarketEventStores.Limits(4, 8);

  private final MarketEventStores stores = new MarketEventStores(2, LIMITS, LIMITS);

  @Test
  void aTradeGoesToTheStoreOfItsInstrument() {
    EventWindow<Trade> first = stores.trades(0).slidingCount(4);
    EventWindow<Trade> second = stores.trades(1).slidingCount(4);

    stores.appendTrade(trade(1, 1, 1000));

    assertThat(first.size()).isZero();
    assertThat(second.size()).isEqualTo(1);
    assertThat(second.get(0).tradeId()).isEqualTo(1000);
  }

  @Test
  void aTopOfBookUpdateGoesToTheStoreOfItsInstrument() {
    EventWindow<BestBidAsk> first = stores.bestBidAsks(0).slidingCount(4);
    EventWindow<BestBidAsk> second = stores.bestBidAsks(1).slidingCount(4);

    stores.appendBestBidAsk(bestBidAsk(0, 1));

    assertThat(first.size()).isEqualTo(1);
    assertThat(second.size()).isZero();
  }

  @Test
  void tradesAndTopOfBookAreKeptApart() {
    EventWindow<Trade> trades = stores.trades(0).slidingCount(4);
    EventWindow<BestBidAsk> bestBidAsks = stores.bestBidAsks(0).slidingCount(4);

    stores.appendTrade(trade(0, 1, 1000));

    assertThat(trades.size()).isEqualTo(1);
    assertThat(bestBidAsks.size()).isZero();
  }

  @Test
  void storesUseTheConfiguredLimits() {
    assertThat(stores.trades(0).capacity()).isEqualTo(LIMITS.initial());
    assertThat(stores.bestBidAsks(1).capacity()).isEqualTo(LIMITS.initial());
  }
}
