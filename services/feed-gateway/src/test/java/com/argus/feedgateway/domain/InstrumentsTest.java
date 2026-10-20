package com.argus.feedgateway.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InstrumentsTest {

  private static final Instrument BTCUSDT = new Instrument("BINANCE", "BTCUSDT");
  private static final Instrument ETHUSDT = new Instrument("BINANCE", "ETHUSDT");

  private final Instruments instruments = new Instruments(List.of(BTCUSDT, ETHUSDT));

  @Test
  void numbersInstrumentsInConfigurationOrder() {
    assertThat(instruments.size()).isEqualTo(2);
    assertThat(instruments.get(0)).isEqualTo(BTCUSDT);
    assertThat(instruments.get(1)).isEqualTo(ETHUSDT);
  }

  @Test
  void findsAnInstrumentBySymbolBytes() {
    byte[] buffer = new byte[25];
    int length = copy("ETHUSDT", buffer);

    assertThat(instruments.indexOf(buffer, length)).isEqualTo(1);
  }

  @Test
  void doesNotFindUnknownOrPartialSymbols() {
    byte[] buffer = new byte[25];

    assertThat(instruments.indexOf(buffer, copy("SOLUSDT", buffer))).isEqualTo(Instruments.UNKNOWN);
    assertThat(instruments.indexOf(buffer, copy("BTCUSDTX", buffer)))
        .isEqualTo(Instruments.UNKNOWN);
    assertThat(instruments.indexOf(buffer, copy("BTCUSD", buffer))).isEqualTo(Instruments.UNKNOWN);
  }

  @Test
  void requiresAtLeastOneInstrument() {
    assertThatIllegalArgumentException().isThrownBy(() -> new Instruments(List.of()));
  }

  @Test
  void rejectsDuplicateSymbols() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Instruments(List.of(BTCUSDT, new Instrument("BINANCE", "BTCUSDT"))))
        .withMessageContaining("BINANCE:BTCUSDT");
  }

  @Test
  void instrumentsAreEqualByVenueAndSymbol() {
    assertThat(new Instrument("BINANCE", "BTCUSDT"))
        .isEqualTo(BTCUSDT)
        .hasSameHashCodeAs(BTCUSDT)
        .isNotEqualTo(ETHUSDT)
        .isNotEqualTo(new Instrument("KRAKEN", "BTCUSDT"))
        .hasToString("BINANCE:BTCUSDT");
    assertThat(BTCUSDT.venue()).isEqualTo("BINANCE");
    assertThat(BTCUSDT.symbol()).isEqualTo("BTCUSDT");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "btcusdt", "BTC-USDT", "BTCUSDT€", "ABCDEFGHIJKLMNOPQRSTUVWXY"})
  void rejectsSymbolsThatDoNotFitTheContract(String symbol) {
    assertThatIllegalArgumentException().isThrownBy(() -> new Instrument("BINANCE", symbol));
  }

  @Test
  void rejectsVenuesThatDoNotFitTheContract() {
    assertThatIllegalArgumentException().isThrownBy(() -> new Instrument("BINANCEBINANC3X", "BTC"));
    assertThatIllegalArgumentException().isThrownBy(() -> new Instrument("binance", "BTC"));
  }

  private static int copy(String symbol, byte[] buffer) {
    byte[] bytes = symbol.getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(bytes, 0, buffer, 0, bytes.length);
    return bytes.length;
  }
}
