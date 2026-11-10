package com.argus.modelengine.application;

import static com.argus.modelengine.application.TestMarketData.bestBidAsk;
import static com.argus.modelengine.application.TestMarketData.trade;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.argus.modelengine.domain.BestBidAsk;
import com.argus.modelengine.domain.EventWindow;
import com.argus.modelengine.domain.Trade;
import com.argus.modelengine.domain.WindowDefinition;
import org.junit.jupiter.api.Test;

class MarketEventStoresTest {

  private static final WindowDefinition SLIDING_4 = WindowDefinition.sliding(4);

  private final MarketEventStores stores =
      new MarketEventStores(new int[] {4, 4}, new int[] {4, 4});

  @Test
  void aTradeGoesToTheStoreOfItsInstrument() {
    EventWindow<Trade> first = stores.trades(0).window(SLIDING_4);
    EventWindow<Trade> second = stores.trades(1).window(SLIDING_4);

    stores.appendTrade(trade(1, 1, 1000));

    assertThat(first.size()).isZero();
    assertThat(second.size()).isEqualTo(1);
    assertThat(second.get(0).tradeId()).isEqualTo(1000);
  }

  @Test
  void aTopOfBookUpdateGoesToTheStoreOfItsInstrument() {
    EventWindow<BestBidAsk> first = stores.bestBidAsks(0).window(SLIDING_4);
    EventWindow<BestBidAsk> second = stores.bestBidAsks(1).window(SLIDING_4);

    stores.appendBestBidAsk(bestBidAsk(0, 1));

    assertThat(first.size()).isEqualTo(1);
    assertThat(second.size()).isZero();
  }

  @Test
  void tradesAndTopOfBookAreKeptApart() {
    EventWindow<Trade> trades = stores.trades(0).window(SLIDING_4);
    EventWindow<BestBidAsk> bestBidAsks = stores.bestBidAsks(0).window(SLIDING_4);

    stores.appendTrade(trade(0, 1, 1000));

    assertThat(trades.size()).isEqualTo(1);
    assertThat(bestBidAsks.size()).isZero();
  }

  @Test
  void eachStoreKeepsItsOwnCapacity() {
    MarketEventStores sized = new MarketEventStores(new int[] {1, 7}, new int[] {199, 1});

    assertThat(sized.trades(0).capacity()).isEqualTo(1);
    assertThat(sized.trades(1).capacity()).isEqualTo(7);
    assertThat(sized.bestBidAsks(0).capacity()).isEqualTo(199);
    assertThat(sized.bestBidAsks(1).capacity()).isEqualTo(1);
  }

  @Test
  void needsOneCapacityPerInstrumentForEachKind() {
    assertThatThrownBy(() -> new MarketEventStores(new int[] {1, 1}, new int[] {1}))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
