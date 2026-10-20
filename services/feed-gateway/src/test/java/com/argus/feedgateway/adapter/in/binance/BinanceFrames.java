package com.argus.feedgateway.adapter.in.binance;

import com.argus.feedgateway.adapter.in.binance.sbe.BestBidAskStreamEventEncoder;
import com.argus.feedgateway.adapter.in.binance.sbe.BoolEnum;
import com.argus.feedgateway.adapter.in.binance.sbe.MessageHeaderEncoder;
import com.argus.feedgateway.adapter.in.binance.sbe.TradesStreamEventEncoder;
import org.agrona.MutableDirectBuffer;

/**
 * Writes synthetic Binance SBE frames with Binance's own encoders, so tests never need real market
 * data.
 */
final class BinanceFrames {

  record Trade(long id, long price, long quantity, boolean buyerIsMaker) {}

  private final MessageHeaderEncoder header = new MessageHeaderEncoder();
  private final TradesStreamEventEncoder trades = new TradesStreamEventEncoder();
  private final BestBidAskStreamEventEncoder bestBidAsk = new BestBidAskStreamEventEncoder();

  /** Writes a {@code TradesStreamEvent} at {@code offset} and returns its length. */
  int trades(
      MutableDirectBuffer buffer,
      int offset,
      String symbol,
      long eventTimeMicros,
      long transactTimeMicros,
      int priceExponent,
      int quantityExponent,
      Trade... entries) {
    trades
        .wrapAndApplyHeader(buffer, offset, header)
        .eventTime(eventTimeMicros)
        .transactTime(transactTimeMicros)
        .priceExponent((byte) priceExponent)
        .qtyExponent((byte) quantityExponent);
    TradesStreamEventEncoder.TradesEncoder group = trades.tradesCount(entries.length);
    for (Trade entry : entries) {
      group
          .next()
          .id(entry.id())
          .price(entry.price())
          .qty(entry.quantity())
          .isBuyerMaker(entry.buyerIsMaker() ? BoolEnum.True : BoolEnum.False);
    }
    trades.symbol(symbol);
    return MessageHeaderEncoder.ENCODED_LENGTH + trades.encodedLength();
  }

  /** Writes a {@code BestBidAskStreamEvent} at {@code offset} and returns its length. */
  int bestBidAsk(
      MutableDirectBuffer buffer,
      int offset,
      String symbol,
      long eventTimeMicros,
      long bookUpdateId,
      int priceExponent,
      int quantityExponent,
      long bidPrice,
      long bidQuantity,
      long askPrice,
      long askQuantity) {
    bestBidAsk
        .wrapAndApplyHeader(buffer, offset, header)
        .eventTime(eventTimeMicros)
        .bookUpdateId(bookUpdateId)
        .priceExponent((byte) priceExponent)
        .qtyExponent((byte) quantityExponent)
        .bidPrice(bidPrice)
        .bidQty(bidQuantity)
        .askPrice(askPrice)
        .askQty(askQuantity)
        .symbol(symbol);
    return MessageHeaderEncoder.ENCODED_LENGTH + bestBidAsk.encodedLength();
  }
}
