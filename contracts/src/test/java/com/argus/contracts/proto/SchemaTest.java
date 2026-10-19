package com.argus.contracts.proto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.argus.contracts.proto.alerts.v1.Alert;
import com.argus.contracts.proto.alerts.v1.Severity;
import com.argus.contracts.proto.indicators.v1.IndicatorValue;
import com.argus.contracts.proto.market.quotes.v1.BestBidAsk;
import com.argus.contracts.proto.market.trades.v1.Side;
import com.argus.contracts.proto.market.trades.v1.Trade;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.protobuf.UnknownFieldSet;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Guards the wire contract. A failure here means the schema changed in a way that consumers will
 * notice: review the evolution rules in contracts/README.md before updating the expected values.
 */
class SchemaTest {

  private static @Nullable Document sbeSchema;

  @BeforeAll
  static void readSbeSchema() throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    try (InputStream schema = SchemaTest.class.getResourceAsStream("/sbe/argus-fastpath.xml")) {
      assertNotNull(schema, "SBE schema on the classpath");
      sbeSchema = factory.newDocumentBuilder().parse(schema);
    }
  }

  static Stream<Arguments> topics() {
    return Stream.of(
        Arguments.of("argus.market.trades.v1", Trade.getDescriptor()),
        Arguments.of("argus.market.quotes.v1", BestBidAsk.getDescriptor()),
        Arguments.of("argus.indicators.v1", IndicatorValue.getDescriptor()),
        Arguments.of("argus.alerts.v1", Alert.getDescriptor()));
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
  void indicatorValueFieldsAreStable() {
    assertFields(
        IndicatorValue.getDescriptor(),
        "1 sequence uint64",
        "2 trigger_sequence uint64",
        "3 trigger_exchange_timestamp int64",
        "4 emit_timestamp int64",
        "5 deployment_id int64",
        "6 deployment_revision uint32",
        "7 values repeated double",
        "8 instruments repeated message");
    assertFields(IndicatorValue.Instrument.getDescriptor(), "1 venue string", "2 symbol string");
  }

  @Test
  void alertFieldsAreStable() {
    assertFields(
        Alert.getDescriptor(),
        "1 sequence uint64",
        "2 trigger_sequence uint64",
        "3 trigger_exchange_timestamp int64",
        "4 emit_timestamp int64",
        "5 deployment_id int64",
        "6 deployment_revision uint32",
        "7 severity enum",
        "8 instruments repeated message",
        "9 message string");
    assertFields(Alert.Instrument.getDescriptor(), "1 venue string", "2 symbol string");
  }

  @Test
  void enumValuesAreStable() {
    assertValues(Side.getDescriptor(), "0 SIDE_UNSPECIFIED", "1 SIDE_BUY", "2 SIDE_SELL");
    assertValues(
        Severity.getDescriptor(),
        "0 SEVERITY_UNSPECIFIED",
        "1 SEVERITY_INFO",
        "2 SEVERITY_WARNING",
        "3 SEVERITY_CRITICAL");
  }

  @Test
  void fieldNumbersMatchTheSbeIds() {
    assertMatchesSbe(Trade.getDescriptor(), sbeMessage("Trade"));
    assertMatchesSbe(BestBidAsk.getDescriptor(), sbeMessage("BestBidAsk"));
    assertMatchesSbe(IndicatorValue.getDescriptor(), sbeMessage("IndicatorValue"));
    assertMatchesSbe(Alert.getDescriptor(), sbeMessage("Alert"));
  }

  @Test
  void enumValuesMatchTheSbeEnums() {
    assertSameValue(com.argus.contracts.sbe.Side.BUY.value(), Side.SIDE_BUY);
    assertSameValue(com.argus.contracts.sbe.Side.SELL.value(), Side.SIDE_SELL);
    assertSameValue(com.argus.contracts.sbe.Severity.INFO.value(), Severity.SEVERITY_INFO);
    assertSameValue(com.argus.contracts.sbe.Severity.WARNING.value(), Severity.SEVERITY_WARNING);
    assertSameValue(com.argus.contracts.sbe.Severity.CRITICAL.value(), Severity.SEVERITY_CRITICAL);
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
    String type = field.getType().name().toLowerCase(Locale.ROOT);
    return field.isRepeated() ? "repeated " + type : type;
  }

  private static void assertValues(EnumDescriptor type, String... expected) {
    List<String> actual =
        type.getValues().stream().map(v -> v.getNumber() + " " + v.getName()).toList();
    assertEquals(List.of(expected), actual, type.getFullName());
  }

  private static Element sbeMessage(String name) {
    assertNotNull(sbeSchema);
    NodeList messages = sbeSchema.getElementsByTagName("sbe:message");
    for (int i = 0; i < messages.getLength(); i++) {
      Element message = (Element) messages.item(i);
      if (message.getAttribute("name").equals(name)) {
        return message;
      }
    }
    throw new AssertionError("SBE message " + name + " not found");
  }

  /**
   * Every Protobuf field number must be the id of the SBE field, group or data element with the
   * same name. Nested messages are compared with the SBE group of the same field.
   */
  private static void assertMatchesSbe(Descriptor message, Element sbeElement) {
    for (FieldDescriptor field : message.getFields()) {
      Element sbeChild = sbeChild(sbeElement, field.getJsonName());
      String where = message.getName() + "." + field.getName();
      assertEquals(Integer.parseInt(sbeChild.getAttribute("id")), field.getNumber(), where);
      if (field.getType() == FieldDescriptor.Type.MESSAGE) {
        assertMatchesSbe(field.getMessageType(), sbeChild);
      }
    }
  }

  private static Element sbeChild(Element parent, String name) {
    for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof Element child && child.getAttribute("name").equals(name)) {
        return child;
      }
    }
    throw new AssertionError(
        "no SBE element " + name + " in " + parent.getAttribute("name") + "; check the names");
  }

  private static void assertSameValue(short sbeValue, ProtocolMessageEnum protoValue) {
    assertEquals(sbeValue, protoValue.getNumber(), protoValue.toString());
  }
}
