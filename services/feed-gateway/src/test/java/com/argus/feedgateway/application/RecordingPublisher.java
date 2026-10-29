package com.argus.feedgateway.application;

import com.argus.feedgateway.application.port.out.MarketDataPublisher;
import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.BestBidAsk;
import com.argus.feedgateway.domain.Instrument;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps a copy of everything published, for assertions: the objects it is passed are reused by the
 * caller. Allocates, so it is not for allocation tests.
 */
public final class RecordingPublisher implements MarketDataPublisher {

  public record Trade(
      Instrument instrument,
      long tradeId,
      long price,
      long quantity,
      long exchangeTimestamp,
      long receiveTimestamp,
      AggressorSide aggressorSide) {}

  public record BestBidAsk(
      Instrument instrument,
      long bookUpdateId,
      long bidPrice,
      long bidQuantity,
      long askPrice,
      long askQuantity,
      long exchangeTimestamp,
      long receiveTimestamp) {}

  public final List<Trade> trades = new ArrayList<>();
  public final List<BestBidAsk> bestBidAsks = new ArrayList<>();
  public final List<Long> heartbeats = new ArrayList<>();

  @Override
  public void publishTrade(com.argus.feedgateway.domain.Trade trade) {
    trades.add(
        new Trade(
            trade.instrument(),
            trade.tradeId(),
            trade.price(),
            trade.quantity(),
            trade.exchangeTimestamp(),
            trade.receiveTimestamp(),
            trade.aggressorSide()));
  }

  @Override
  public void publishBestBidAsk(com.argus.feedgateway.domain.BestBidAsk book) {
    bestBidAsks.add(
        new BestBidAsk(
            book.instrument(),
            book.bookUpdateId(),
            book.bidPrice(),
            book.bidQuantity(),
            book.askPrice(),
            book.askQuantity(),
            book.exchangeTimestamp(),
            book.receiveTimestamp()));
  }

  @Override
  public void publishHeartbeat(long epochNow) {
    heartbeats.add(epochNow);
  }
}
