package com.argus.contracts.proto;

import static com.argus.contracts.proto.Messages.BTC_PRICE;
import static com.argus.contracts.proto.Messages.MAX_SYMBOL;
import static com.argus.contracts.proto.Messages.MAX_UINT64;
import static com.argus.contracts.proto.Messages.MAX_VENUE;
import static com.argus.contracts.proto.Messages.ONE;
import static com.argus.contracts.proto.Messages.ONE_UNIT;
import static com.argus.contracts.proto.Messages.TIMESTAMP;
import static com.argus.contracts.proto.Messages.assertRoundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.argus.contracts.proto.market.candles.v1.Candle;
import com.argus.contracts.proto.market.candles.v1.Interval;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CandleMessageTest {

  /** 2026-10-15T09:30:00Z, aligned to the minute. */
  private static final long MINUTE_OPEN = 1_792_056_600_000_000_000L;

  static Stream<Candle> candles() {
    return Stream.of(
        Candle.newBuilder()
            .setVenue("BINANCE")
            .setSymbol("BTCUSDT")
            .setInterval(Interval.INTERVAL_1M)
            .setOpenTimestamp(MINUTE_OPEN)
            .setOpen(BTC_PRICE)
            .setHigh(BTC_PRICE + 2_500_000_000L)
            .setLow(BTC_PRICE - 1_000_000_000L)
            .setClose(BTC_PRICE + 500_000_000L)
            .setVolume(3 * ONE + 41_000_000L)
            .setQuoteVolume(22_442_710_723_456_789L)
            .setTradeCount(1_284)
            .setEmitTimestamp(TIMESTAMP)
            .build(),
        Candle.newBuilder()
            .setVenue(MAX_VENUE)
            .setSymbol(MAX_SYMBOL)
            .setInterval(Interval.INTERVAL_1S)
            .setOpenTimestamp(Long.MAX_VALUE)
            .setOpen(Long.MAX_VALUE)
            .setHigh(Long.MAX_VALUE)
            .setLow(Long.MAX_VALUE)
            .setClose(Long.MAX_VALUE)
            .setVolume(Long.MAX_VALUE)
            .setQuoteVolume(Long.MAX_VALUE)
            .setTradeCount(MAX_UINT64)
            .setEmitTimestamp(Long.MAX_VALUE)
            .build(),
        Candle.newBuilder()
            .setVenue("BINANCE")
            .setSymbol("BTCUSDT")
            .setInterval(Interval.INTERVAL_1S)
            .setOpenTimestamp(0)
            .setOpen(ONE_UNIT)
            .setHigh(ONE_UNIT)
            .setLow(ONE_UNIT)
            .setClose(ONE_UNIT)
            .setVolume(ONE_UNIT)
            .setQuoteVolume(0)
            .setTradeCount(1)
            .setEmitTimestamp(0)
            .build(),
        Candle.newBuilder()
            .setInterval(Interval.INTERVAL_1M)
            .setOpenTimestamp(Long.MIN_VALUE)
            .setOpen(Long.MIN_VALUE)
            .setHigh(Long.MIN_VALUE)
            .setLow(Long.MIN_VALUE)
            .setClose(Long.MIN_VALUE)
            .setVolume(Long.MIN_VALUE)
            .setQuoteVolume(Long.MIN_VALUE)
            .setTradeCount(1)
            .setEmitTimestamp(Long.MIN_VALUE)
            .build());
  }

  @ParameterizedTest
  @MethodSource("candles")
  void roundTrip(Candle candle) throws InvalidProtocolBufferException {
    assertRoundTrip(candle, Candle.parser());
  }

  @Test
  void emptyMessageDecodesToDefaults() throws InvalidProtocolBufferException {
    Candle candle = Candle.parseFrom(new byte[0]);

    assertEquals(Interval.INTERVAL_UNSPECIFIED, candle.getInterval());
    assertEquals("", candle.getSymbol());
    assertEquals(0, candle.getTradeCount());
  }
}
