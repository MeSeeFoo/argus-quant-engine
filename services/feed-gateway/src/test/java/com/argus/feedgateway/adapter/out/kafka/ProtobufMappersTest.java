package com.argus.feedgateway.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.argus.contracts.proto.market.trades.v1.Side;
import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.BestBidAsk;
import com.argus.feedgateway.domain.Instrument;
import com.argus.feedgateway.domain.Instruments;
import com.argus.feedgateway.domain.Trade;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProtobufMappersTest {

  private static final Instruments INSTRUMENTS =
      new Instruments(
          List.of(new Instrument("BINANCE", "BTCUSDT"), new Instrument("BINANCE", "ETHUSDT")));
  // As the relay passes them: from the shard's instruments, with their id, which a slot stores
  private static final Instrument BTCUSDT = INSTRUMENTS.get(0);
  private static final Instrument ETHUSDT = INSTRUMENTS.get(1);

  @Test
  void mapsEveryFieldOfATrade() {
    var message =
        ProtobufMappers.trade(
            new Trade()
                .instrument(BTCUSDT)
                .tradeId(1001)
                .price(6_421_050_000_000L, -8)
                .quantity(12_000_000, -8)
                .aggressorSide(AggressorSide.SELL)
                .exchangeTimestamp(11)
                .receiveTimestamp(12),
            7);

    assertThat(message.getSequence()).isEqualTo(7);
    assertThat(message.getTradeId()).isEqualTo(1001);
    assertThat(message.getPrice()).isEqualTo(6_421_050_000_000L);
    assertThat(message.getQuantity()).isEqualTo(12_000_000);
    assertThat(message.getAggressorSide()).isEqualTo(Side.SIDE_SELL);
    assertThat(message.getExchangeTimestamp()).isEqualTo(11);
    assertThat(message.getReceiveTimestamp()).isEqualTo(12);
    assertThat(message.getVenue()).isEqualTo("BINANCE");
    assertThat(message.getSymbol()).isEqualTo("BTCUSDT");
  }

  @ParameterizedTest
  @CsvSource({"BUY, SIDE_BUY", "SELL, SIDE_SELL"})
  void mapsTheAggressorSide(AggressorSide side, Side expected) {
    var message = ProtobufMappers.trade(trade().aggressorSide(side), 1);

    assertThat(message.getAggressorSide()).isEqualTo(expected);
  }

  @Test
  void mapsEveryFieldOfATopOfBook() {
    var message =
        ProtobufMappers.bestBidAsk(
            new BestBidAsk()
                .instrument(BTCUSDT)
                .bookUpdateId(77)
                .bid(6_421_050_000_000L, -8, 3_000, -8)
                .ask(6_421_051_000_000L, -8, 120_000, -8)
                .exchangeTimestamp(21)
                .receiveTimestamp(22),
            8);

    assertThat(message.getSequence()).isEqualTo(8);
    assertThat(message.getBookUpdateId()).isEqualTo(77);
    assertThat(message.getBidPrice()).isEqualTo(6_421_050_000_000L);
    assertThat(message.getBidQuantity()).isEqualTo(3_000);
    assertThat(message.getAskPrice()).isEqualTo(6_421_051_000_000L);
    assertThat(message.getAskQuantity()).isEqualTo(120_000);
    assertThat(message.getExchangeTimestamp()).isEqualTo(21);
    assertThat(message.getReceiveTimestamp()).isEqualTo(22);
    assertThat(message.getVenue()).isEqualTo("BINANCE");
    assertThat(message.getSymbol()).isEqualTo("BTCUSDT");
  }

  @Test
  void keysRecordsByVenueAndSymbol() {
    assertThat(ProtobufMappers.key(BTCUSDT)).isEqualTo("BINANCE:BTCUSDT");
  }

  /** What the publisher thread does: read the slot the agent wrote, then map it. */
  @Test
  void aTradeSurvivesTheSlotAndTheMapping() {
    var buffer = new UnsafeBuffer(new byte[KafkaTradeSlot.LENGTH]);
    Trade written =
        trade().price(345_678, -2).quantity(15, -3).aggressorSide(AggressorSide.SELL).tradeId(42);
    KafkaTradeSlot.write(buffer, 0, 9, written);

    Trade read = new Trade();
    KafkaTradeSlot.read(buffer, 0, INSTRUMENTS, read);
    var message = ProtobufMappers.trade(read, buffer.getLong(0));

    assertThat(message).isEqualTo(ProtobufMappers.trade(written, 9));
    assertThat(message.getPrice()).as("canonical scale").isEqualTo(345_678_000_000L);
    assertThat(message.getQuantity()).isEqualTo(1_500_000L);
  }

  @Test
  void aTopOfBookSurvivesTheSlotAndTheMapping() {
    var buffer = new UnsafeBuffer(new byte[KafkaBidAskSlot.LENGTH]);
    BestBidAsk written =
        new BestBidAsk()
            .instrument(ETHUSDT)
            .bookUpdateId(5)
            .bid(1, 0, 2, 0)
            .ask(3, 0, 4, 0)
            .exchangeTimestamp(31)
            .receiveTimestamp(32);
    KafkaBidAskSlot.write(buffer, 0, 10, written);

    BestBidAsk read = new BestBidAsk();
    KafkaBidAskSlot.read(buffer, 0, INSTRUMENTS, read);
    var message = ProtobufMappers.bestBidAsk(read, buffer.getLong(0));

    assertThat(message).isEqualTo(ProtobufMappers.bestBidAsk(written, 10));
  }

  private static Trade trade() {
    return new Trade()
        .instrument(BTCUSDT)
        .tradeId(1)
        .price(1, 0)
        .quantity(1, 0)
        .aggressorSide(AggressorSide.BUY)
        .exchangeTimestamp(1)
        .receiveTimestamp(1);
  }
}
