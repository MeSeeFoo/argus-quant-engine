package com.argus.contracts.proto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;

/** Shared values and helpers for the round-trip tests. */
final class Messages {

  /** Largest uint64: Protobuf has no null value, so every bit pattern is valid. */
  static final long MAX_UINT64 = -1L;

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

  private Messages() {}

  /** Serialises the message, parses it back and checks that nothing changed. */
  static <T extends Message> T assertRoundTrip(T message, Parser<T> parser)
      throws InvalidProtocolBufferException {
    byte[] bytes = message.toByteArray();
    assertEquals(message.getSerializedSize(), bytes.length);

    T parsed = parser.parseFrom(bytes);
    assertEquals(message, parsed);
    return parsed;
  }
}
