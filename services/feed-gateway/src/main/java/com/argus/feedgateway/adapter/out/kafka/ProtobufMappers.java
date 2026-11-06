package com.argus.feedgateway.adapter.out.kafka;

import com.argus.contracts.proto.market.trades.v1.Side;
import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.BestBidAsk;
import com.argus.feedgateway.domain.Instrument;
import com.argus.feedgateway.domain.Trade;

/**
 * Maps the gateway's events to the canonical Protobuf messages of the durable path ({@code
 * contracts/README.md}). Values are copied as they are: the events already hold {@code Decimal64}
 * mantissas at the contract's scale, as the fast path does.
 *
 * <p>Called on the Kafka publisher's thread, which allocates a message per record anyway; never on
 * the gateway agent's thread.
 */
final class ProtobufMappers {

  private ProtobufMappers() {}

  /** {@code trade}, published with {@code sequence}, as the message of the trades topic. */
  static com.argus.contracts.proto.market.trades.v1.Trade trade(Trade trade, long sequence) {
    Instrument instrument = trade.instrument();
    return com.argus.contracts.proto.market.trades.v1.Trade.newBuilder()
        .setSequence(sequence)
        .setTradeId(trade.tradeId())
        .setPrice(trade.price())
        .setQuantity(trade.quantity())
        .setExchangeTimestamp(trade.exchangeTimestamp())
        .setReceiveTimestamp(trade.receiveTimestamp())
        .setAggressorSide(side(trade.aggressorSide()))
        .setVenue(instrument.venue())
        .setSymbol(instrument.symbol())
        .build();
  }

  /** {@code book}, published with {@code sequence}, as the message of the quotes topic. */
  static com.argus.contracts.proto.market.quotes.v1.BestBidAsk bestBidAsk(
      BestBidAsk book, long sequence) {
    Instrument instrument = book.instrument();
    return com.argus.contracts.proto.market.quotes.v1.BestBidAsk.newBuilder()
        .setSequence(sequence)
        .setBookUpdateId(book.bookUpdateId())
        .setBidPrice(book.bidPrice())
        .setBidQuantity(book.bidQuantity())
        .setAskPrice(book.askPrice())
        .setAskQuantity(book.askQuantity())
        .setExchangeTimestamp(book.exchangeTimestamp())
        .setReceiveTimestamp(book.receiveTimestamp())
        .setVenue(instrument.venue())
        .setSymbol(instrument.symbol())
        .build();
  }

  /**
   * The record key of every market data topic, {@code VENUE:SYMBOL}: it spreads instruments over
   * partitions and keeps each one in order.
   */
  static String key(Instrument instrument) {
    return instrument.toString();
  }

  // Exhaustive: a new AggressorSide fails to compile here instead of publishing SIDE_UNSPECIFIED
  private static Side side(AggressorSide side) {
    return switch (side) {
      case BUY -> Side.SIDE_BUY;
      case SELL -> Side.SIDE_SELL;
    };
  }
}
