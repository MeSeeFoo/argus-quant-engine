package com.argus.contracts.sbe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Guards the wire contract. A failure here means the schema changed in a way that consumers will
 * notice: review the versioning rules in contracts/README.md before updating the expected values.
 */
class SchemaTest {

  @Test
  void schemaIdAndVersionAreExplicit() {
    assertEquals(1, MessageHeaderEncoder.SCHEMA_ID);
    assertEquals(0, MessageHeaderEncoder.SCHEMA_VERSION);
  }

  @Test
  void decimalsUseAFixedScaleOfEightDigits() {
    assertEquals(-8, new Decimal64Decoder().exponent());
  }

  static Stream<Arguments> messages() {
    return Stream.of(
        Arguments.of("Trade", TradeEncoder.TEMPLATE_ID, TradeEncoder.BLOCK_LENGTH, 1, 85),
        Arguments.of(
            "BestBidAsk", BestBidAskEncoder.TEMPLATE_ID, BestBidAskEncoder.BLOCK_LENGTH, 2, 100),
        Arguments.of("Signal", SignalEncoder.TEMPLATE_ID, SignalEncoder.BLOCK_LENGTH, 10, 91),
        Arguments.of(
            "Heartbeat", HeartbeatEncoder.TEMPLATE_ID, HeartbeatEncoder.BLOCK_LENGTH, 99, 16));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("messages")
  void templateIdAndBlockLengthAreStable(
      String message,
      int templateId,
      int blockLength,
      int expectedTemplateId,
      int expectedBlockLength) {
    assertEquals(expectedTemplateId, templateId, message + " templateId");
    assertEquals(expectedBlockLength, blockLength, message + " blockLength");
  }

  @Test
  void sixtyFourBitFieldsAreEightByteAligned() {
    int[] offsets = {
      TradeEncoder.sequenceEncodingOffset(),
      TradeEncoder.tradeIdEncodingOffset(),
      TradeEncoder.priceEncodingOffset(),
      TradeEncoder.quantityEncodingOffset(),
      TradeEncoder.exchangeTimestampEncodingOffset(),
      TradeEncoder.receiveTimestampEncodingOffset(),
      BestBidAskEncoder.sequenceEncodingOffset(),
      BestBidAskEncoder.bookUpdateIdEncodingOffset(),
      BestBidAskEncoder.bidPriceEncodingOffset(),
      BestBidAskEncoder.bidQuantityEncodingOffset(),
      BestBidAskEncoder.askPriceEncodingOffset(),
      BestBidAskEncoder.askQuantityEncodingOffset(),
      BestBidAskEncoder.exchangeTimestampEncodingOffset(),
      BestBidAskEncoder.receiveTimestampEncodingOffset(),
      SignalEncoder.signalIdEncodingOffset(),
      SignalEncoder.triggerSequenceEncodingOffset(),
      SignalEncoder.strengthEncodingOffset(),
      SignalEncoder.referencePriceEncodingOffset(),
      SignalEncoder.triggerExchangeTimestampEncodingOffset(),
      SignalEncoder.emitTimestampEncodingOffset(),
      HeartbeatEncoder.sequenceEncodingOffset(),
      HeartbeatEncoder.timestampEncodingOffset(),
    };
    for (int offset : offsets) {
      assertEquals(0, (MessageHeaderEncoder.ENCODED_LENGTH + offset) % 8, "offset " + offset);
    }
  }
}
