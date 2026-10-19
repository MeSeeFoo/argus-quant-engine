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

class AlertCodecTest {

  record Instrument(String venue, String symbol) {}

  record Alert(
      long sequence,
      long triggerSequence,
      long triggerExchangeTimestamp,
      long emitTimestamp,
      long deploymentId,
      long deploymentRevision,
      Severity severity,
      List<Instrument> instruments,
      String message) {}

  private final UnsafeBuffer buffer = Codecs.buffer();
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final AlertEncoder encoder = new AlertEncoder();
  private final AlertDecoder decoder = new AlertDecoder();

  static Stream<Alert> alerts() {
    return Stream.of(
        new Alert(
            1,
            2,
            TIMESTAMP,
            TIMESTAMP + 12_000,
            42,
            3,
            Severity.WARNING,
            List.of(new Instrument("BINANCE", "BTCUSDT")),
            "RSI(14) below 30 in an uptrend"),
        new Alert(
            MAX_SEQUENCE,
            MAX_SEQUENCE,
            MAX_INT64,
            MAX_INT64,
            MAX_INT64,
            MAX_UINT32,
            Severity.CRITICAL,
            List.of(new Instrument(MAX_VENUE, MAX_SYMBOL), new Instrument("BINANCE", "ETHUSDT")),
            "Spread BTC/ETH > 3σ · ñ €"),
        new Alert(1, 1, 0, 0, MIN_INT64, 0, Severity.INFO, List.of(), ""));
  }

  @ParameterizedTest
  @MethodSource("alerts")
  void roundTrip(Alert alert) {
    encoder
        .wrapAndApplyHeader(buffer, OFFSET, headerEncoder)
        .sequence(alert.sequence())
        .triggerSequence(alert.triggerSequence())
        .triggerExchangeTimestamp(alert.triggerExchangeTimestamp())
        .emitTimestamp(alert.emitTimestamp())
        .deploymentId(alert.deploymentId())
        .deploymentRevision(alert.deploymentRevision())
        .severity(alert.severity());
    AlertEncoder.InstrumentsEncoder instruments =
        encoder.instrumentsCount(alert.instruments().size());
    for (Instrument instrument : alert.instruments()) {
      instruments.next().venue(instrument.venue()).symbol(instrument.symbol());
    }
    encoder.message(alert.message());

    headerDecoder.wrap(buffer, OFFSET);
    assertEquals(AlertEncoder.TEMPLATE_ID, headerDecoder.templateId());
    decoder.wrapAndApplyHeader(buffer, OFFSET, headerDecoder);
    long sequence = decoder.sequence();
    long triggerSequence = decoder.triggerSequence();
    long triggerExchangeTimestamp = decoder.triggerExchangeTimestamp();
    long emitTimestamp = decoder.emitTimestamp();
    long deploymentId = decoder.deploymentId();
    long deploymentRevision = decoder.deploymentRevision();
    Severity severity = decoder.severity();
    List<Instrument> decodedInstruments = new ArrayList<>();
    for (AlertDecoder.InstrumentsDecoder i : decoder.instruments()) {
      decodedInstruments.add(new Instrument(i.venue(), i.symbol()));
    }
    String message = decoder.message();

    assertEquals(
        alert,
        new Alert(
            sequence,
            triggerSequence,
            triggerExchangeTimestamp,
            emitTimestamp,
            deploymentId,
            deploymentRevision,
            severity,
            decodedInstruments,
            message));
    assertEquals(encoder.encodedLength(), decoder.encodedLength());
  }
}
