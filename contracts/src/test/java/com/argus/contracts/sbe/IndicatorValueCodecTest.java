package com.argus.contracts.sbe;

import static com.argus.contracts.sbe.Codecs.MAX_INT64;
import static com.argus.contracts.sbe.Codecs.MAX_SEQUENCE;
import static com.argus.contracts.sbe.Codecs.MAX_SYMBOL;
import static com.argus.contracts.sbe.Codecs.MAX_UINT32;
import static com.argus.contracts.sbe.Codecs.MAX_VENUE;
import static com.argus.contracts.sbe.Codecs.MIN_INT64;
import static com.argus.contracts.sbe.Codecs.OFFSET;
import static com.argus.contracts.sbe.Codecs.TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class IndicatorValueCodecTest {

  record Instrument(String venue, String symbol) {}

  record IndicatorValue(
      long sequence,
      long triggerSequence,
      long triggerExchangeTimestamp,
      long emitTimestamp,
      long deploymentId,
      long deploymentRevision,
      List<Long> valueMantissas,
      List<Instrument> instruments) {}

  /** The mantissa of an output while the model does not have enough data. */
  private static final long NO_VALUE = Long.MIN_VALUE;

  private final UnsafeBuffer buffer = Codecs.buffer();
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final IndicatorValueEncoder encoder = new IndicatorValueEncoder();
  private final IndicatorValueDecoder decoder = new IndicatorValueDecoder();

  static Stream<IndicatorValue> indicatorValues() {
    return Stream.of(
        // A VWAP on one instrument.
        new IndicatorValue(
            1,
            2,
            TIMESTAMP,
            TIMESTAMP + 12_000,
            42,
            3,
            List.of(6_543_210_123_456L),
            List.of(new Instrument("BINANCE", "BTCUSDT"))),
        // Bollinger bands of a spread between two instruments.
        new IndicatorValue(
            MAX_SEQUENCE,
            MAX_SEQUENCE,
            MAX_INT64,
            MAX_INT64,
            MAX_INT64,
            MAX_UINT32,
            List.of(0L, MAX_INT64, MIN_INT64, -1L),
            List.of(new Instrument(MAX_VENUE, MAX_SYMBOL), new Instrument("BINANCE", "ETHUSDT"))),
        // Not enough data yet, and empty groups.
        new IndicatorValue(1, 1, 0, 0, MIN_INT64, 0, List.of(NO_VALUE, NO_VALUE), List.of()),
        new IndicatorValue(1, 1, 0, 0, 0, 0, List.of(), List.of()));
  }

  @ParameterizedTest
  @MethodSource("indicatorValues")
  void roundTrip(IndicatorValue value) {
    encoder
        .wrapAndApplyHeader(buffer, OFFSET, headerEncoder)
        .sequence(value.sequence())
        .triggerSequence(value.triggerSequence())
        .triggerExchangeTimestamp(value.triggerExchangeTimestamp())
        .emitTimestamp(value.emitTimestamp())
        .deploymentId(value.deploymentId())
        .deploymentRevision(value.deploymentRevision());
    IndicatorValueEncoder.ValueMantissasEncoder values =
        encoder.valueMantissasCount(value.valueMantissas().size());
    for (long mantissa : value.valueMantissas()) {
      values.next().value().mantissa(mantissa);
    }
    IndicatorValueEncoder.InstrumentsEncoder instruments =
        encoder.instrumentsCount(value.instruments().size());
    for (Instrument instrument : value.instruments()) {
      instruments.next().venue(instrument.venue()).symbol(instrument.symbol());
    }

    headerDecoder.wrap(buffer, OFFSET);
    assertEquals(IndicatorValueEncoder.TEMPLATE_ID, headerDecoder.templateId());
    decoder.wrapAndApplyHeader(buffer, OFFSET, headerDecoder);
    long sequence = decoder.sequence();
    long triggerSequence = decoder.triggerSequence();
    long triggerExchangeTimestamp = decoder.triggerExchangeTimestamp();
    long emitTimestamp = decoder.emitTimestamp();
    long deploymentId = decoder.deploymentId();
    long deploymentRevision = decoder.deploymentRevision();
    List<Long> decodedValues = new ArrayList<>();
    for (IndicatorValueDecoder.ValueMantissasDecoder v : decoder.valueMantissas()) {
      decodedValues.add(v.value().mantissa());
    }
    List<Instrument> decodedInstruments = new ArrayList<>();
    for (IndicatorValueDecoder.InstrumentsDecoder i : decoder.instruments()) {
      decodedInstruments.add(new Instrument(i.venue(), i.symbol()));
    }

    assertEquals(
        value,
        new IndicatorValue(
            sequence,
            triggerSequence,
            triggerExchangeTimestamp,
            emitTimestamp,
            deploymentId,
            deploymentRevision,
            decodedValues,
            decodedInstruments));
    assertEquals(encoder.encodedLength(), decoder.encodedLength());
  }
}
