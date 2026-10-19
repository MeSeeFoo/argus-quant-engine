package com.argus.contracts.proto;

import static com.argus.contracts.proto.Messages.MAX_SYMBOL;
import static com.argus.contracts.proto.Messages.MAX_UINT64;
import static com.argus.contracts.proto.Messages.MAX_VENUE;
import static com.argus.contracts.proto.Messages.TIMESTAMP;
import static com.argus.contracts.proto.Messages.assertRoundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.argus.contracts.proto.indicators.v1.IndicatorValue;
import com.argus.contracts.proto.indicators.v1.IndicatorValue.Instrument;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class IndicatorValueMessageTest {

  /** Largest uint32, read back as a negative int in Java. */
  private static final int MAX_UINT32 = -1;

  static Stream<IndicatorValue> indicatorValues() {
    return Stream.of(
        indicatorValue(
            42,
            9_001,
            TIMESTAMP,
            TIMESTAMP + 2_300_000,
            7,
            3,
            List.of(65_432.10123456),
            List.of(instrument("BINANCE", "BTCUSDT"))),
        indicatorValue(
            MAX_UINT64,
            MAX_UINT64,
            Long.MAX_VALUE,
            Long.MAX_VALUE,
            Long.MAX_VALUE,
            MAX_UINT32,
            List.of(-0.0, Double.MAX_VALUE, -Double.MIN_VALUE),
            List.of(instrument(MAX_VENUE, MAX_SYMBOL), instrument("BINANCE", "ETHUSDT"))),
        indicatorValue(
            1,
            1,
            Long.MIN_VALUE,
            Long.MIN_VALUE,
            Long.MIN_VALUE,
            0,
            List.of(Double.NaN, Double.NEGATIVE_INFINITY),
            List.of()));
  }

  @ParameterizedTest
  @MethodSource("indicatorValues")
  void roundTrip(IndicatorValue value) throws InvalidProtocolBufferException {
    assertRoundTrip(value, IndicatorValue.parser());
  }

  @Test
  void valuesKeepTheirOrder() throws InvalidProtocolBufferException {
    IndicatorValue bands = indicatorValue(1, 1, 1, 1, 1, 1, List.of(1.0, 2.0, 3.0), List.of());

    assertEquals(
        List.of(1.0, 2.0, 3.0), assertRoundTrip(bands, IndicatorValue.parser()).getValuesList());
  }

  @Test
  void notEnoughDataIsNaN() throws InvalidProtocolBufferException {
    IndicatorValue warmingUp = indicatorValue(1, 1, 1, 1, 1, 1, List.of(Double.NaN), List.of());

    assertTrue(Double.isNaN(assertRoundTrip(warmingUp, IndicatorValue.parser()).getValues(0)));
  }

  @Test
  void emptyMessageDecodesToDefaults() throws InvalidProtocolBufferException {
    IndicatorValue value = IndicatorValue.parseFrom(new byte[0]);

    assertEquals(0, value.getDeploymentId());
    assertEquals(0, value.getValuesCount());
    assertEquals(0, value.getInstrumentsCount());
  }

  private static Instrument instrument(String venue, String symbol) {
    return Instrument.newBuilder().setVenue(venue).setSymbol(symbol).build();
  }

  private static IndicatorValue indicatorValue(
      long sequence,
      long triggerSequence,
      long triggerExchangeTimestamp,
      long emitTimestamp,
      long deploymentId,
      int deploymentRevision,
      List<Double> values,
      List<Instrument> instruments) {
    return IndicatorValue.newBuilder()
        .setSequence(sequence)
        .setTriggerSequence(triggerSequence)
        .setTriggerExchangeTimestamp(triggerExchangeTimestamp)
        .setEmitTimestamp(emitTimestamp)
        .setDeploymentId(deploymentId)
        .setDeploymentRevision(deploymentRevision)
        .addAllValues(values)
        .addAllInstruments(instruments)
        .build();
  }
}
