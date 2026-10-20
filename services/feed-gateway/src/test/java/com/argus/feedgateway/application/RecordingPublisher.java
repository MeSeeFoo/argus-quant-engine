package com.argus.feedgateway.application;

import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.Instrument;
import java.util.ArrayList;
import java.util.List;

/** Keeps everything published, for assertions. Allocates: not for allocation tests. */
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
  public final List<long[]> heartbeatChecks = new ArrayList<>();

  @Override
  public void publishTrade(
      Instrument instrument,
      long tradeId,
      long price,
      long quantity,
      long exchangeTimestamp,
      long receiveTimestamp,
      AggressorSide aggressorSide) {
    trades.add(
        new Trade(
            instrument,
            tradeId,
            price,
            quantity,
            exchangeTimestamp,
            receiveTimestamp,
            aggressorSide));
  }

  @Override
  public void publishBestBidAsk(
      Instrument instrument,
      long bookUpdateId,
      long bidPrice,
      long bidQuantity,
      long askPrice,
      long askQuantity,
      long exchangeTimestamp,
      long receiveTimestamp) {
    bestBidAsks.add(
        new BestBidAsk(
            instrument,
            bookUpdateId,
            bidPrice,
            bidQuantity,
            askPrice,
            askQuantity,
            exchangeTimestamp,
            receiveTimestamp));
  }

  @Override
  public int heartbeatIfIdle(long monotonicNow, long epochNow) {
    heartbeatChecks.add(new long[] {monotonicNow, epochNow});
    return 0;
  }
}
