package com.argus.contracts.sbe;

import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** Shared values and buffers for the round-trip tests. */
final class Codecs {

  /** Largest value of an SBE uint64; all bits set is reserved as the null value. */
  static final long MAX_SEQUENCE = 0xFFFF_FFFF_FFFF_FFFEL;

  /** Smallest int64 SBE accepts; {@link Long#MIN_VALUE} is reserved as the null value. */
  static final long MIN_INT64 = Long.MIN_VALUE + 1;

  static final long MAX_INT64 = Long.MAX_VALUE;

  /** 65 432.10123456, a realistic BTCUSDT price at scale 1e-8. */
  static final long BTC_PRICE = 6_543_210_123_456L;

  /** 0.00000001, the smallest positive amount at scale 1e-8. */
  static final long ONE_UNIT = 1L;

  /** 1.0 at scale 1e-8. */
  static final long ONE = 100_000_000L;

  /** 2026-10-15T09:30:00.123456789Z. */
  static final long TIMESTAMP = 1_792_056_600_123_456_789L;

  static final String MAX_SYMBOL = "ABCDEFGHIJKLMNOPQRSTUVWX";
  static final String MAX_VENUE = "ABCDEFGHIJKL";

  /** Messages are written away from offset zero so the tests do not depend on it. */
  static final int OFFSET = 64;

  private Codecs() {}

  static UnsafeBuffer buffer() {
    return new UnsafeBuffer(ByteBuffer.allocateDirect(512));
  }
}
