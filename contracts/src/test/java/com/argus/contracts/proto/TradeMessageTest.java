package com.argus.contracts.proto;

import static com.argus.contracts.proto.Messages.BTC_PRICE;
import static com.argus.contracts.proto.Messages.MAX_SYMBOL;
import static com.argus.contracts.proto.Messages.MAX_UINT64;
import static com.argus.contracts.proto.Messages.MAX_VENUE;
import static com.argus.contracts.proto.Messages.ONE_UNIT;
import static com.argus.contracts.proto.Messages.TIMESTAMP;
import static com.argus.contracts.proto.Messages.assertRoundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.argus.contracts.proto.market.trades.v1.Side;
import com.argus.contracts.proto.market.trades.v1.Trade;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TradeMessageTest {

  static Stream<Trade> trades() {
    return Stream.of(
        trade(
            1,
            4_181_022_597L,
            BTC_PRICE,
            12_340_000L,
            TIMESTAMP,
            TIMESTAMP + 850_000,
            Side.SIDE_BUY,
            "BINANCE",
            "BTCUSDT"),
        trade(
            MAX_UINT64,
            Long.MAX_VALUE,
            Long.MAX_VALUE,
            Long.MAX_VALUE,
            Long.MAX_VALUE,
            Long.MAX_VALUE,
            Side.SIDE_SELL,
            MAX_VENUE,
            MAX_SYMBOL),
        trade(1, 0, ONE_UNIT, ONE_UNIT, 0, 0, Side.SIDE_BUY, "BINANCE", "BTCUSDT"),
        trade(
            1,
            Long.MIN_VALUE,
            Long.MIN_VALUE,
            Long.MIN_VALUE,
            Long.MIN_VALUE,
            Long.MIN_VALUE,
            Side.SIDE_SELL,
            "",
            ""));
  }

  @ParameterizedTest
  @MethodSource("trades")
  void roundTrip(Trade trade) throws InvalidProtocolBufferException {
    assertRoundTrip(trade, Trade.parser());
  }

  @Test
  void emptyMessageDecodesToDefaults() throws InvalidProtocolBufferException {
    Trade trade = Trade.parseFrom(new byte[0]);

    assertEquals(Side.SIDE_UNSPECIFIED, trade.getAggressorSide());
    assertEquals("", trade.getSymbol());
    assertEquals(0, trade.getPrice());
  }

  private static Trade trade(
      long sequence,
      long tradeId,
      long price,
      long quantity,
      long exchangeTimestamp,
      long receiveTimestamp,
      Side aggressorSide,
      String venue,
      String symbol) {
    return Trade.newBuilder()
        .setSequence(sequence)
        .setTradeId(tradeId)
        .setPrice(price)
        .setQuantity(quantity)
        .setExchangeTimestamp(exchangeTimestamp)
        .setReceiveTimestamp(receiveTimestamp)
        .setAggressorSide(aggressorSide)
        .setVenue(venue)
        .setSymbol(symbol)
        .build();
  }
}
