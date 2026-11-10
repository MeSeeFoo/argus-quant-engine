package com.argus.modelengine.application;

import static com.argus.modelengine.application.TestMarketData.bestBidAsk;
import static com.argus.modelengine.application.TestMarketData.trade;
import static org.assertj.core.api.Assertions.assertThat;

import com.argus.modelengine.domain.feed.SequenceGapCounter;
import com.argus.modelengine.domain.feed.TradeDeduplicator;
import com.argus.modelengine.domain.marketdata.BestBidAsk;
import com.argus.modelengine.domain.marketdata.Trade;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketDataReaderTest {

  private static final int SESSION = 5;

  private final TradeDeduplicator deduplicator = new TradeDeduplicator(2);
  private final SequenceGapCounter gaps = new SequenceGapCounter();
  private final List<Trade> trades = new ArrayList<>();
  private final List<BestBidAsk> bestBidAsks = new ArrayList<>();
  private final MarketDataReader reader =
      new MarketDataReader(deduplicator, gaps, trades::add, bestBidAsks::add);

  @Test
  void passesOnNewTradesAndTopOfBookUpdates() {
    Trade trade = trade(0, 1, 1000);
    BestBidAsk book = bestBidAsk(0, 2);

    reader.onTrade(SESSION, trade);
    reader.onBestBidAsk(SESSION, book);

    assertThat(trades).containsExactly(trade);
    assertThat(bestBidAsks).containsExactly(book);
  }

  @Test
  void dropsAndCountsDuplicateAndOldTrades() {
    reader.onTrade(SESSION, trade(0, 1, 1000));
    reader.onTrade(SESSION, trade(0, 2, 1000));
    reader.onTrade(SESSION, trade(0, 3, 999));

    assertThat(trades).hasSize(1);
    assertThat(deduplicator.droppedTrades()).isEqualTo(2);
  }

  @Test
  void tradeIdsAreComparedPerInstrument() {
    reader.onTrade(SESSION, trade(0, 1, 1000));
    reader.onTrade(SESSION, trade(1, 2, 5));

    assertThat(trades).hasSize(2);
  }

  @Test
  void aDroppedTradeStillTakesItsSequence() {
    reader.onTrade(SESSION, trade(0, 1, 1000));
    reader.onTrade(SESSION, trade(0, 2, 1000));
    reader.onTrade(SESSION, trade(0, 3, 1001));

    assertThat(gaps.missedMessages()).isZero();
  }

  @Test
  void countsJumpsInTheSequence() {
    reader.onTrade(SESSION, trade(0, 1, 1000));
    reader.onBestBidAsk(SESSION, bestBidAsk(0, 4));

    assertThat(gaps.missedMessages()).isEqualTo(2);
  }

  @Test
  void anIgnoredEventIsNotAGap() {
    reader.onTrade(SESSION, trade(0, 1, 1000));
    reader.onIgnored(SESSION, 2);
    reader.onTrade(SESSION, trade(0, 3, 1001));

    assertThat(gaps.missedMessages()).isZero();
  }

  @Test
  void aHeartbeatRevealsMessagesLostBeforeIt() {
    reader.onTrade(SESSION, trade(0, 1, 1000));
    reader.onHeartbeat(SESSION, 3);

    assertThat(gaps.missedMessages()).isEqualTo(2);
  }
}
