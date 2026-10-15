package com.argus.contracts.proto;

import static com.argus.contracts.proto.Messages.BTC_PRICE;
import static com.argus.contracts.proto.Messages.MAX_SYMBOL;
import static com.argus.contracts.proto.Messages.MAX_UINT64;
import static com.argus.contracts.proto.Messages.MAX_VENUE;
import static com.argus.contracts.proto.Messages.ONE;
import static com.argus.contracts.proto.Messages.TIMESTAMP;
import static com.argus.contracts.proto.Messages.assertRoundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.argus.contracts.proto.signals.v1.Direction;
import com.argus.contracts.proto.signals.v1.Signal;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SignalMessageTest {

  /** Largest uint32, read back as a negative int in Java. */
  private static final int MAX_UINT32 = -1;

  static Stream<Signal> signals() {
    return Stream.of(
        signal(
            42,
            9_001,
            75_000_000L,
            BTC_PRICE,
            TIMESTAMP,
            TIMESTAMP + 2_300_000,
            7,
            3,
            Direction.DIRECTION_LONG,
            "BINANCE",
            "BTCUSDT"),
        signal(
            MAX_UINT64,
            MAX_UINT64,
            ONE,
            Long.MAX_VALUE,
            Long.MAX_VALUE,
            Long.MAX_VALUE,
            MAX_UINT32,
            MAX_UINT32,
            Direction.DIRECTION_SHORT,
            MAX_VENUE,
            MAX_SYMBOL),
        signal(
            1,
            1,
            0,
            Long.MIN_VALUE,
            Long.MIN_VALUE,
            Long.MIN_VALUE,
            0,
            0,
            Direction.DIRECTION_FLAT,
            "",
            ""));
  }

  @ParameterizedTest
  @MethodSource("signals")
  void roundTrip(Signal signal) throws InvalidProtocolBufferException {
    assertRoundTrip(signal, Signal.parser());
  }

  @Test
  void emptyMessageDecodesToDefaults() throws InvalidProtocolBufferException {
    Signal signal = Signal.parseFrom(new byte[0]);

    assertEquals(Direction.DIRECTION_UNSPECIFIED, signal.getDirection());
    assertEquals("", signal.getSymbol());
    assertEquals(0, signal.getStrength());
  }

  @Test
  void flatIsNotTheDefaultDirection() throws InvalidProtocolBufferException {
    Signal flat = signal(1, 1, ONE, BTC_PRICE, 1, 1, 1, 1, Direction.DIRECTION_FLAT, "B", "S");

    assertEquals(Direction.DIRECTION_FLAT, assertRoundTrip(flat, Signal.parser()).getDirection());
    assertEquals(Direction.DIRECTION_UNSPECIFIED, Signal.getDefaultInstance().getDirection());
  }

  private static Signal signal(
      long signalId,
      long triggerSequence,
      long strength,
      long referencePrice,
      long triggerExchangeTimestamp,
      long emitTimestamp,
      int ruleId,
      int ruleVersion,
      Direction direction,
      String venue,
      String symbol) {
    return Signal.newBuilder()
        .setSignalId(signalId)
        .setTriggerSequence(triggerSequence)
        .setStrength(strength)
        .setReferencePrice(referencePrice)
        .setTriggerExchangeTimestamp(triggerExchangeTimestamp)
        .setEmitTimestamp(emitTimestamp)
        .setRuleId(ruleId)
        .setRuleVersion(ruleVersion)
        .setDirection(direction)
        .setVenue(venue)
        .setSymbol(symbol)
        .build();
  }
}
