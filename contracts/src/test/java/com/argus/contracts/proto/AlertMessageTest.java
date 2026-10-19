package com.argus.contracts.proto;

import static com.argus.contracts.proto.Messages.MAX_SYMBOL;
import static com.argus.contracts.proto.Messages.MAX_UINT64;
import static com.argus.contracts.proto.Messages.MAX_VENUE;
import static com.argus.contracts.proto.Messages.TIMESTAMP;
import static com.argus.contracts.proto.Messages.assertRoundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.argus.contracts.proto.alerts.v1.Alert;
import com.argus.contracts.proto.alerts.v1.Alert.Instrument;
import com.argus.contracts.proto.alerts.v1.Severity;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AlertMessageTest {

  /** Largest uint32, read back as a negative int in Java. */
  private static final int MAX_UINT32 = -1;

  static Stream<Alert> alerts() {
    return Stream.of(
        alert(
            42,
            9_001,
            TIMESTAMP,
            TIMESTAMP + 2_300_000,
            7,
            3,
            Severity.SEVERITY_WARNING,
            List.of(instrument("BINANCE", "BTCUSDT")),
            "RSI(14) below 30 in an uptrend"),
        alert(
            MAX_UINT64,
            MAX_UINT64,
            Long.MAX_VALUE,
            Long.MAX_VALUE,
            Long.MAX_VALUE,
            MAX_UINT32,
            Severity.SEVERITY_CRITICAL,
            List.of(instrument(MAX_VENUE, MAX_SYMBOL), instrument("BINANCE", "ETHUSDT")),
            "Spread BTC/ETH > 3σ · ñ €"),
        alert(
            1,
            1,
            Long.MIN_VALUE,
            Long.MIN_VALUE,
            Long.MIN_VALUE,
            0,
            Severity.SEVERITY_INFO,
            List.of(),
            ""));
  }

  @ParameterizedTest
  @MethodSource("alerts")
  void roundTrip(Alert alert) throws InvalidProtocolBufferException {
    assertRoundTrip(alert, Alert.parser());
  }

  @Test
  void emptyMessageDecodesToDefaults() throws InvalidProtocolBufferException {
    Alert alert = Alert.parseFrom(new byte[0]);

    assertEquals(Severity.SEVERITY_UNSPECIFIED, alert.getSeverity());
    assertEquals("", alert.getMessage());
    assertEquals(0, alert.getInstrumentsCount());
  }

  private static Instrument instrument(String venue, String symbol) {
    return Instrument.newBuilder().setVenue(venue).setSymbol(symbol).build();
  }

  private static Alert alert(
      long sequence,
      long triggerSequence,
      long triggerExchangeTimestamp,
      long emitTimestamp,
      long deploymentId,
      int deploymentRevision,
      Severity severity,
      List<Instrument> instruments,
      String message) {
    return Alert.newBuilder()
        .setSequence(sequence)
        .setTriggerSequence(triggerSequence)
        .setTriggerExchangeTimestamp(triggerExchangeTimestamp)
        .setEmitTimestamp(emitTimestamp)
        .setDeploymentId(deploymentId)
        .setDeploymentRevision(deploymentRevision)
        .setSeverity(severity)
        .addAllInstruments(instruments)
        .setMessage(message)
        .build();
  }
}
