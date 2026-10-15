package com.argus.contracts.sbe;

import static com.argus.contracts.sbe.Codecs.BTC_PRICE;
import static com.argus.contracts.sbe.Codecs.MAX_INT64;
import static com.argus.contracts.sbe.Codecs.MAX_SEQUENCE;
import static com.argus.contracts.sbe.Codecs.MAX_SYMBOL;
import static com.argus.contracts.sbe.Codecs.MAX_VENUE;
import static com.argus.contracts.sbe.Codecs.MIN_INT64;
import static com.argus.contracts.sbe.Codecs.OFFSET;
import static com.argus.contracts.sbe.Codecs.ONE_UNIT;
import static com.argus.contracts.sbe.Codecs.TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class BestBidAskCodecTest {

  record BestBidAsk(
      long sequence,
      long bookUpdateId,
      long bidPrice,
      long bidQuantity,
      long askPrice,
      long askQuantity,
      long exchangeTimestamp,
      long receiveTimestamp,
      String venue,
      String symbol) {}

  private final UnsafeBuffer buffer = Codecs.buffer();
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final BestBidAskEncoder encoder = new BestBidAskEncoder();
  private final BestBidAskDecoder decoder = new BestBidAskDecoder();

  static Stream<BestBidAsk> quotes() {
    return Stream.of(
        new BestBidAsk(
            2,
            72_118_334_910L,
            BTC_PRICE,
            51_200_000L,
            BTC_PRICE + 1_000_000,
            3_000_000L,
            TIMESTAMP,
            TIMESTAMP + 850_000,
            "BINANCE",
            "BTCUSDT"),
        new BestBidAsk(
            MAX_SEQUENCE,
            MAX_INT64,
            MAX_INT64,
            MAX_INT64,
            MAX_INT64,
            MAX_INT64,
            MAX_INT64,
            MAX_INT64,
            MAX_VENUE,
            MAX_SYMBOL),
        new BestBidAsk(1, 0, ONE_UNIT, ONE_UNIT, ONE_UNIT, ONE_UNIT, 0, 0, "BINANCE", "BTCUSDT"),
        new BestBidAsk(1, MIN_INT64, MIN_INT64, 0, MIN_INT64, 0, MIN_INT64, MIN_INT64, "", ""));
  }

  @ParameterizedTest
  @MethodSource("quotes")
  void roundTrip(BestBidAsk quote) {
    encoder
        .wrapAndApplyHeader(buffer, OFFSET, headerEncoder)
        .sequence(quote.sequence())
        .bookUpdateId(quote.bookUpdateId());
    encoder.bidPrice().mantissa(quote.bidPrice());
    encoder.bidQuantity().mantissa(quote.bidQuantity());
    encoder.askPrice().mantissa(quote.askPrice());
    encoder.askQuantity().mantissa(quote.askQuantity());
    encoder
        .exchangeTimestamp(quote.exchangeTimestamp())
        .receiveTimestamp(quote.receiveTimestamp())
        .venue(quote.venue())
        .symbol(quote.symbol());

    headerDecoder.wrap(buffer, OFFSET);
    assertEquals(BestBidAskEncoder.TEMPLATE_ID, headerDecoder.templateId());
    decoder.wrapAndApplyHeader(buffer, OFFSET, headerDecoder);
    BestBidAsk decoded =
        new BestBidAsk(
            decoder.sequence(),
            decoder.bookUpdateId(),
            decoder.bidPrice().mantissa(),
            decoder.bidQuantity().mantissa(),
            decoder.askPrice().mantissa(),
            decoder.askQuantity().mantissa(),
            decoder.exchangeTimestamp(),
            decoder.receiveTimestamp(),
            decoder.venue(),
            decoder.symbol());

    assertEquals(quote, decoded);
  }
}
