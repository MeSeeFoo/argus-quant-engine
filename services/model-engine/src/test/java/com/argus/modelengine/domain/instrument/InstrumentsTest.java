package com.argus.modelengine.domain.instrument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstrumentsTest {

  private final Instruments instruments =
      new Instruments(
          List.of(
              Instrument.parse("BINANCE:BTCUSDT"),
              Instrument.parse("BINANCE:ETHUSDT"),
              Instrument.parse("KRAKEN:BTCUSDT")));

  private static byte[] field(String value, int length) {
    return Arrays.copyOf(value.getBytes(StandardCharsets.US_ASCII), length);
  }

  private int indexOf(String venue, String symbol) {
    return instruments.indexOf(
        field(venue, Instrument.VENUE_LENGTH), field(symbol, Instrument.SYMBOL_LENGTH));
  }

  @Test
  void numbersInstrumentsInConfigurationOrder() {
    assertThat(instruments.size()).isEqualTo(3);
    assertThat(instruments.get(1)).isEqualTo(new Instrument("BINANCE", "ETHUSDT"));
  }

  @Test
  void findsAnInstrumentByItsRawFields() {
    assertThat(indexOf("BINANCE", "ETHUSDT")).isEqualTo(1);
  }

  @Test
  void theVenueIsPartOfTheInstrument() {
    assertThat(indexOf("KRAKEN", "BTCUSDT")).isEqualTo(2);
  }

  @Test
  void anInstrumentNotConfiguredIsUnknown() {
    assertThat(indexOf("BINANCE", "SOLUSDT")).isEqualTo(Instruments.UNKNOWN);
    assertThat(indexOf("KRAKEN", "ETHUSDT")).isEqualTo(Instruments.UNKNOWN);
  }

  @Test
  void aSymbolThatOnlyStartsLikeAnInstrumentIsUnknown() {
    assertThat(indexOf("BINANCE", "BTCUSD")).isEqualTo(Instruments.UNKNOWN);
  }

  @Test
  void rejectsAnEmptyListOrDuplicates() {
    assertThatThrownBy(() -> new Instruments(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    Instrument btc = Instrument.parse("BINANCE:BTCUSDT");
    assertThatThrownBy(() -> new Instruments(List.of(btc, btc)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("BINANCE:BTCUSDT");
  }

  @Test
  void parsesVenueAndSymbol() {
    Instrument instrument = Instrument.parse("BINANCE:BTCUSDT");

    assertThat(instrument.venue()).isEqualTo("BINANCE");
    assertThat(instrument.symbol()).isEqualTo("BTCUSDT");
    assertThat(instrument).hasToString("BINANCE:BTCUSDT");
  }

  @Test
  void rejectsMalformedKeys() {
    assertThatThrownBy(() -> Instrument.parse("BTCUSDT"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Instrument.parse("A:B:C"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsValuesThatDoNotFitTheCanonicalFields() {
    assertThatThrownBy(() -> new Instrument("", "BTCUSDT"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Instrument("BINANCE", "btcusdt"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Instrument("VENUEWITH13CH", "BTCUSDT"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
