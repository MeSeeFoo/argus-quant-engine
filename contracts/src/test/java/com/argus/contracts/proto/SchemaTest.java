package com.argus.contracts.proto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.argus.contracts.proto.market.quotes.v1.BestBidAsk;
import com.argus.contracts.proto.market.trades.v1.Side;
import com.argus.contracts.proto.market.trades.v1.Trade;
import com.argus.contracts.proto.signals.v1.Direction;
import com.argus.contracts.proto.signals.v1.Signal;
import com.argus.contracts.sbe.BestBidAskEncoder;
import com.argus.contracts.sbe.SignalEncoder;
import com.argus.contracts.sbe.TradeEncoder;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.protobuf.UnknownFieldSet;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Guards the wire contract. A failure here means the schema changed in a way that consumers will
 * notice: review the evolution rules in contracts/README.md before updating the expected values.
 */
class SchemaTest {

  static Stream<Arguments> topics() {
    return Stream.of(
        Arguments.of("argus.market.trades.v1", Trade.getDescriptor()),
        Arguments.of("argus.market.quotes.v1", BestBidAsk.getDescriptor()),
        Arguments.of("argus.signals.v1", Signal.getDescriptor()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("topics")
  void protoPackageIsTheTopicNameAndHoldsOneMessage(String topic, Descriptor message) {
    assertEquals(topic, message.getFile().getPackage());
    assertEquals(List.of(message), message.getFile().getMessageTypes());
    assertEquals("proto3", message.getFile().toProto().getSyntax());
  }

  @Test
  void tradeFieldsAreStable() {
    assertFields(
        Trade.getDescriptor(),
        "1 sequence uint64",
        "2 trade_id int64",
        "3 price int64",
        "4 quantity int64",
        "5 exchange_timestamp int64",
        "6 receive_timestamp int64",
        "7 aggressor_side enum",
        "8 venue string",
        "9 symbol string");
  }

  @Test
  void bestBidAskFieldsAreStable() {
    assertFields(
        BestBidAsk.getDescriptor(),
        "1 sequence uint64",
        "2 book_update_id int64",
        "3 bid_price int64",
        "4 bid_quantity int64",
        "5 ask_price int64",
        "6 ask_quantity int64",
        "7 exchange_timestamp int64",
        "8 receive_timestamp int64",
        "9 venue string",
        "10 symbol string");
  }

  @Test
  void signalFieldsAreStable() {
    assertFields(
        Signal.getDescriptor(),
        "1 signal_id uint64",
        "2 trigger_sequence uint64",
        "3 strength int64",
        "4 reference_price int64",
        "5 trigger_exchange_timestamp int64",
        "6 emit_timestamp int64",
        "7 rule_id uint32",
        "8 rule_version uint32",
        "9 direction enum",
        "10 venue string",
        "11 symbol string");
  }

  @Test
  void enumValuesAreStable() {
    assertValues(Side.getDescriptor(), "0 SIDE_UNSPECIFIED", "1 SIDE_BUY", "2 SIDE_SELL");
    assertValues(
        Direction.getDescriptor(),
        "0 DIRECTION_UNSPECIFIED",
        "1 DIRECTION_LONG",
        "2 DIRECTION_SHORT",
        "3 DIRECTION_FLAT");
  }

  @Test
  void fieldNumbersMatchTheSbeFieldIds() throws ReflectiveOperationException {
    assertMatchesSbe(Trade.getDescriptor(), TradeEncoder.class);
    assertMatchesSbe(BestBidAsk.getDescriptor(), BestBidAskEncoder.class);
    assertMatchesSbe(Signal.getDescriptor(), SignalEncoder.class);
  }

  @Test
  void enumValuesMatchTheSbeEnums() {
    assertSameValue(com.argus.contracts.sbe.Side.BUY.value(), Side.SIDE_BUY);
    assertSameValue(com.argus.contracts.sbe.Side.SELL.value(), Side.SIDE_SELL);
    assertSameValue(com.argus.contracts.sbe.Direction.LONG.value(), Direction.DIRECTION_LONG);
    assertSameValue(com.argus.contracts.sbe.Direction.SHORT.value(), Direction.DIRECTION_SHORT);
  }

  @Test
  void currentReaderAcceptsAMessageFromANewerWriter() throws IOException {
    Trade trade =
        Trade.newBuilder().setSequence(7).setVenue("BINANCE").setSymbol("BTCUSDT").build();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    trade.writeTo(bytes);
    // A field a future version could add, with a number this version does not know.
    CodedOutputStream extra = CodedOutputStream.newInstance(bytes);
    extra.writeInt64(100, 123_456L);
    extra.flush();

    Trade parsed = Trade.parseFrom(bytes.toByteArray());

    assertEquals(
        trade, parsed.toBuilder().setUnknownFields(UnknownFieldSet.getDefaultInstance()).build());
    assertEquals(1, parsed.getUnknownFields().asMap().size());
    assertEquals(bytes.size(), parsed.toByteArray().length, "unknown fields are kept on re-write");
  }

  @Test
  void unknownEnumValueIsReportedAsUnrecognized() throws InvalidProtocolBufferException {
    Trade parsed =
        Trade.parseFrom(Trade.newBuilder().setAggressorSideValue(42).build().toByteArray());

    assertEquals(Side.UNRECOGNIZED, parsed.getAggressorSide());
    assertEquals(42, parsed.getAggressorSideValue());
  }

  private static void assertFields(Descriptor message, String... expected) {
    List<String> actual =
        message.getFields().stream()
            .map(f -> f.getNumber() + " " + f.getName() + " " + typeName(f))
            .toList();
    assertEquals(List.of(expected), actual, message.getFullName());
  }

  private static String typeName(FieldDescriptor field) {
    return field.getType().name().toLowerCase(Locale.ROOT);
  }

  private static void assertValues(EnumDescriptor type, String... expected) {
    List<String> actual =
        type.getValues().stream().map(v -> v.getNumber() + " " + v.getName()).toList();
    assertEquals(List.of(expected), actual, type.getFullName());
  }

  private static void assertMatchesSbe(Descriptor message, Class<?> sbeEncoder)
      throws ReflectiveOperationException {
    for (FieldDescriptor field : message.getFields()) {
      int sbeId = (int) sbeEncoder.getMethod(field.getJsonName() + "Id").invoke(null);
      assertEquals(sbeId, field.getNumber(), message.getName() + "." + field.getName());
    }
  }

  private static void assertSameValue(short sbeValue, ProtocolMessageEnum protoValue) {
    assertEquals(sbeValue, protoValue.getNumber(), protoValue.toString());
  }
}
