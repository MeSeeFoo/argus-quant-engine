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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TradeCodecTest {

  record Trade(
      long sequence,
      long tradeId,
      long price,
      long quantity,
      long exchangeTimestamp,
      long receiveTimestamp,
      Side aggressorSide,
      String venue,
      String symbol) {}

  private final UnsafeBuffer buffer = Codecs.buffer();
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final TradeEncoder encoder = new TradeEncoder();
  private final TradeDecoder decoder = new TradeDecoder();

  static Stream<Trade> trades() {
    return Stream.of(
        new Trade(
            1,
            4_181_022_597L,
            BTC_PRICE,
            12_340_000L,
            TIMESTAMP,
            TIMESTAMP + 850_000,
            Side.BUY,
            "BINANCE",
            "BTCUSDT"),
        new Trade(
            MAX_SEQUENCE,
            MAX_INT64,
            MAX_INT64,
            MAX_INT64,
            MAX_INT64,
            MAX_INT64,
            Side.SELL,
            MAX_VENUE,
            MAX_SYMBOL),
        new Trade(1, 0, ONE_UNIT, ONE_UNIT, 0, 0, Side.BUY, "BINANCE", "BTCUSDT"),
        new Trade(1, MIN_INT64, MIN_INT64, 0, MIN_INT64, MIN_INT64, Side.SELL, "", ""));
  }

  @ParameterizedTest
  @MethodSource("trades")
  void roundTrip(Trade trade) {
    int length = encode(trade);

    headerDecoder.wrap(buffer, OFFSET);
    assertEquals(TradeEncoder.TEMPLATE_ID, headerDecoder.templateId());
    assertEquals(MessageHeaderEncoder.ENCODED_LENGTH + TradeEncoder.BLOCK_LENGTH, length);
    assertEquals(trade, decode());
  }

  @Test
  void rejectsSymbolLongerThanTwentyFourCharacters() {
    encoder.wrapAndApplyHeader(buffer, OFFSET, headerEncoder);

    assertThrows(IndexOutOfBoundsException.class, () -> encoder.symbol(MAX_SYMBOL + "Y"));
  }

  @Test
  void rejectsVenueLongerThanTwelveCharacters() {
    encoder.wrapAndApplyHeader(buffer, OFFSET, headerEncoder);

    assertThrows(IndexOutOfBoundsException.class, () -> encoder.venue(MAX_VENUE + "M"));
  }

  @Test
  void shorterSymbolOverwritesALongerOne() {
    encode(new Trade(1, 1, 1, 1, 1, 1, Side.BUY, MAX_VENUE, MAX_SYMBOL));
    encode(new Trade(1, 1, 1, 1, 1, 1, Side.BUY, "BINANCE", "ETHBTC"));

    Trade decoded = decode();
    assertEquals("BINANCE", decoded.venue());
    assertEquals("ETHBTC", decoded.symbol());
  }

  private int encode(Trade trade) {
    encoder
        .wrapAndApplyHeader(buffer, OFFSET, headerEncoder)
        .sequence(trade.sequence())
        .tradeId(trade.tradeId());
    encoder.price().mantissa(trade.price());
    encoder.quantity().mantissa(trade.quantity());
    encoder
        .exchangeTimestamp(trade.exchangeTimestamp())
        .receiveTimestamp(trade.receiveTimestamp())
        .aggressorSide(trade.aggressorSide())
        .venue(trade.venue())
        .symbol(trade.symbol());
    return MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
  }

  private Trade decode() {
    decoder.wrapAndApplyHeader(buffer, OFFSET, headerDecoder);
    return new Trade(
        decoder.sequence(),
        decoder.tradeId(),
        decoder.price().mantissa(),
        decoder.quantity().mantissa(),
        decoder.exchangeTimestamp(),
        decoder.receiveTimestamp(),
        decoder.aggressorSide(),
        decoder.venue(),
        decoder.symbol());
  }
}
