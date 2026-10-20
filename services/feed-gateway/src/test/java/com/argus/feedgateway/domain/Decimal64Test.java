package com.argus.feedgateway.domain;

import static com.argus.feedgateway.domain.Decimal64.NOT_REPRESENTABLE;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class Decimal64Test {

  @ParameterizedTest(name = "{0} x 10^{1} = {2} x 10^-8")
  @CsvSource({
    // Binance BTCUSDT: price with 2 decimals, quantity with 5 or 8
    "6421050, -2, 6421050000000",
    "123, -5, 123000",
    "1, -8, 1",
    "-1, -8, -1",
    // Positive exponents scale up
    "5, 0, 500000000",
    "5, 3, 500000000000",
    // Trailing zeros beyond the eighth decimal are exact
    "1000, -11, 1",
    "-250000000000, -18, -25",
    "0, -100, 0",
    "0, 100, 0",
    // The largest values that fit
    "92233720368, 0, 9223372036800000000",
    "9223372036854775807, -8, 9223372036854775807",
  })
  void convertsExactValues(long mantissa, int exponent, long expected) {
    assertThat(Decimal64.fromDecimal(mantissa, exponent)).isEqualTo(expected);
  }

  @ParameterizedTest(name = "{0} x 10^{1} is not representable")
  @CsvSource({
    // More than eight decimals
    "1, -9",
    "123456789, -17",
    "1, -27",
    "-1, -9",
    // Too large for int64 at exponent -8
    "92233720369, 0",
    "-92233720369, 0",
    "1, 11",
    "1, 127",
    "9223372036854775807, -7",
    // Long.MIN_VALUE is the null value
    "-9223372036854775808, -8",
  })
  void rejectsValuesThatWouldLoseInformation(long mantissa, int exponent) {
    assertThat(Decimal64.fromDecimal(mantissa, exponent)).isEqualTo(NOT_REPRESENTABLE);
  }
}
