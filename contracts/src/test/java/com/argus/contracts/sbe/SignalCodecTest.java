package com.argus.contracts.sbe;

import static com.argus.contracts.sbe.Codecs.BTC_PRICE;
import static com.argus.contracts.sbe.Codecs.MAX_INT64;
import static com.argus.contracts.sbe.Codecs.MAX_SEQUENCE;
import static com.argus.contracts.sbe.Codecs.MAX_SYMBOL;
import static com.argus.contracts.sbe.Codecs.MAX_VENUE;
import static com.argus.contracts.sbe.Codecs.MIN_INT64;
import static com.argus.contracts.sbe.Codecs.OFFSET;
import static com.argus.contracts.sbe.Codecs.ONE;
import static com.argus.contracts.sbe.Codecs.TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SignalCodecTest {

  /** Largest value of an SBE uint32; all bits set is reserved as the null value. */
  private static final long MAX_RULE_ID = 0xFFFF_FFFEL;

  /** Largest value of an SBE uint16; all bits set is reserved as the null value. */
  private static final int MAX_RULE_VERSION = 0xFFFE;

  record Signal(
      long signalId,
      long triggerSequence,
      long strength,
      long referencePrice,
      long triggerExchangeTimestamp,
      long emitTimestamp,
      long ruleId,
      int ruleVersion,
      Direction direction,
      String venue,
      String symbol) {}

  private final UnsafeBuffer buffer = Codecs.buffer();
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final SignalEncoder encoder = new SignalEncoder();
  private final SignalDecoder decoder = new SignalDecoder();

  static Stream<Signal> signals() {
    return Stream.of(
        new Signal(
            1,
            2,
            80_000_000L,
            BTC_PRICE,
            TIMESTAMP,
            TIMESTAMP + 12_000,
            42,
            3,
            Direction.LONG,
            "BINANCE",
            "BTCUSDT"),
        new Signal(
            MAX_SEQUENCE,
            MAX_SEQUENCE,
            ONE,
            MAX_INT64,
            MAX_INT64,
            MAX_INT64,
            MAX_RULE_ID,
            MAX_RULE_VERSION,
            Direction.SHORT,
            MAX_VENUE,
            MAX_SYMBOL),
        new Signal(1, 1, 0, MIN_INT64, 0, 0, 0, 0, Direction.FLAT, "", ""));
  }

  @ParameterizedTest
  @MethodSource("signals")
  void roundTrip(Signal signal) {
    encoder
        .wrapAndApplyHeader(buffer, OFFSET, headerEncoder)
        .signalId(signal.signalId())
        .triggerSequence(signal.triggerSequence());
    encoder.strength().mantissa(signal.strength());
    encoder.referencePrice().mantissa(signal.referencePrice());
    encoder
        .triggerExchangeTimestamp(signal.triggerExchangeTimestamp())
        .emitTimestamp(signal.emitTimestamp())
        .ruleId(signal.ruleId())
        .ruleVersion(signal.ruleVersion())
        .direction(signal.direction())
        .venue(signal.venue())
        .symbol(signal.symbol());

    headerDecoder.wrap(buffer, OFFSET);
    assertEquals(SignalEncoder.TEMPLATE_ID, headerDecoder.templateId());
    decoder.wrapAndApplyHeader(buffer, OFFSET, headerDecoder);
    Signal decoded =
        new Signal(
            decoder.signalId(),
            decoder.triggerSequence(),
            decoder.strength().mantissa(),
            decoder.referencePrice().mantissa(),
            decoder.triggerExchangeTimestamp(),
            decoder.emitTimestamp(),
            decoder.ruleId(),
            decoder.ruleVersion(),
            decoder.direction(),
            decoder.venue(),
            decoder.symbol());

    assertEquals(signal, decoded);
  }
}
