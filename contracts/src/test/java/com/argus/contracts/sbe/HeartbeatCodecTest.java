package com.argus.contracts.sbe;

import static com.argus.contracts.sbe.Codecs.MAX_INT64;
import static com.argus.contracts.sbe.Codecs.MAX_SEQUENCE;
import static com.argus.contracts.sbe.Codecs.MIN_INT64;
import static com.argus.contracts.sbe.Codecs.OFFSET;
import static com.argus.contracts.sbe.Codecs.TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HeartbeatCodecTest {

  private final UnsafeBuffer buffer = Codecs.buffer();
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final HeartbeatEncoder encoder = new HeartbeatEncoder();
  private final HeartbeatDecoder decoder = new HeartbeatDecoder();

  @ParameterizedTest
  @CsvSource({
    "1, " + TIMESTAMP,
    MAX_SEQUENCE + ", " + MAX_INT64,
    "0, " + MIN_INT64,
  })
  void roundTrip(long sequence, long timestamp) {
    encoder
        .wrapAndApplyHeader(buffer, OFFSET, headerEncoder)
        .sequence(sequence)
        .timestamp(timestamp);

    headerDecoder.wrap(buffer, OFFSET);
    assertEquals(HeartbeatEncoder.TEMPLATE_ID, headerDecoder.templateId());
    decoder.wrapAndApplyHeader(buffer, OFFSET, headerDecoder);

    assertEquals(sequence, decoder.sequence());
    assertEquals(timestamp, decoder.timestamp());
  }
}
