package com.argus.contracts.proto;

import static com.argus.contracts.proto.Messages.BTC_PRICE;
import static com.argus.contracts.proto.Messages.MAX_SYMBOL;
import static com.argus.contracts.proto.Messages.MAX_UINT64;
import static com.argus.contracts.proto.Messages.MAX_VENUE;
import static com.argus.contracts.proto.Messages.ONE_UNIT;
import static com.argus.contracts.proto.Messages.TIMESTAMP;
import static com.argus.contracts.proto.Messages.assertRoundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.argus.contracts.proto.market.quotes.v1.BestBidAsk;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class BestBidAskMessageTest {

  static Stream<BestBidAsk> quotes() {
    return Stream.of(
        quote(
            2,
            71_234_567_890L,
            BTC_PRICE,
            51_200_000L,
            BTC_PRICE + 1_000_000L,
            8_700_000L,
            TIMESTAMP,
            TIMESTAMP + 620_000,
            "BINANCE",
            "BTCUSDT"),
        quote(
            MAX_UINT64,
            Long.MAX_VALUE,
            Long.MAX_VALUE,
            Long.MAX_VALUE,
            Long.MAX_VALUE,
            Long.MAX_VALUE,
            Long.MAX_VALUE,
            Long.MAX_VALUE,
            MAX_VENUE,
            MAX_SYMBOL),
        quote(1, 0, ONE_UNIT, ONE_UNIT, ONE_UNIT, ONE_UNIT, 0, 0, "BINANCE", "BTCUSDT"),
        quote(
            1,
            Long.MIN_VALUE,
            Long.MIN_VALUE,
            Long.MIN_VALUE,
            Long.MIN_VALUE,
            Long.MIN_VALUE,
            Long.MIN_VALUE,
            Long.MIN_VALUE,
            "",
            ""));
  }

  @ParameterizedTest
  @MethodSource("quotes")
  void roundTrip(BestBidAsk quote) throws InvalidProtocolBufferException {
    assertRoundTrip(quote, BestBidAsk.parser());
  }

  @Test
  void emptyMessageDecodesToDefaults() throws InvalidProtocolBufferException {
    BestBidAsk quote = BestBidAsk.parseFrom(new byte[0]);

    assertEquals(0, quote.getBookUpdateId());
    assertEquals("", quote.getSymbol());
    assertEquals(0, quote.getBidPrice());
  }

  private static BestBidAsk quote(
      long sequence,
      long bookUpdateId,
      long bidPrice,
      long bidQuantity,
      long askPrice,
      long askQuantity,
      long exchangeTimestamp,
      long receiveTimestamp,
      String venue,
      String symbol) {
    return BestBidAsk.newBuilder()
        .setSequence(sequence)
        .setBookUpdateId(bookUpdateId)
        .setBidPrice(bidPrice)
        .setBidQuantity(bidQuantity)
        .setAskPrice(askPrice)
        .setAskQuantity(askQuantity)
        .setExchangeTimestamp(exchangeTimestamp)
        .setReceiveTimestamp(receiveTimestamp)
        .setVenue(venue)
        .setSymbol(symbol)
        .build();
  }
}
