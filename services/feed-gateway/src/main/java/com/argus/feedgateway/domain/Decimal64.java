package com.argus.feedgateway.domain;

/**
 * Conversion of a venue's decimal (mantissa and exponent) to the canonical {@code Decimal64}: an
 * {@code int64} mantissa with a constant exponent of −8 (ADR 0004). Values are never rounded: one
 * that cannot be represented exactly is reported as {@link #NOT_REPRESENTABLE}.
 */
public final class Decimal64 {

  /** The canonical exponent is {@code -SCALE}. */
  public static final int SCALE = 8;

  /**
   * Returned when a value has more than eight decimals or does not fit in an {@code int64}. It is
   * the SBE null value of {@code int64}, which canonical messages never carry.
   */
  public static final long NOT_REPRESENTABLE = Long.MIN_VALUE;

  private static final long[] POWERS_OF_TEN = new long[19];

  static {
    long power = 1;
    for (int i = 0; i < POWERS_OF_TEN.length; i++) {
      POWERS_OF_TEN[i] = power;
      power *= 10;
    }
  }

  private Decimal64() {}

  /**
   * Returns the canonical mantissa of {@code mantissa × 10^exponent}, or {@link
   * #NOT_REPRESENTABLE}.
   */
  public static long fromDecimal(long mantissa, int exponent) {
    if (mantissa == 0) {
      return 0;
    }
    int shift = exponent + SCALE;
    if (shift >= 0) {
      if (shift >= POWERS_OF_TEN.length) {
        return NOT_REPRESENTABLE;
      }
      long factor = POWERS_OF_TEN[shift];
      long result = mantissa * factor;
      boolean overflow = Math.multiplyHigh(mantissa, factor) != (result >> 63);
      return overflow ? NOT_REPRESENTABLE : result;
    }
    int divisorExponent = -shift;
    if (divisorExponent >= POWERS_OF_TEN.length) {
      // Every non-zero int64 is smaller than 10^19, so it would lose digits.
      return NOT_REPRESENTABLE;
    }
    long divisor = POWERS_OF_TEN[divisorExponent];
    return mantissa % divisor == 0 ? mantissa / divisor : NOT_REPRESENTABLE;
  }
}
