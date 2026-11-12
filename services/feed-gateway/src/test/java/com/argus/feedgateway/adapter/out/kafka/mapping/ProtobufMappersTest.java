package com.argus.feedgateway.adapter.out.kafka.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.argus.contracts.proto.market.trades.v1.Side;
import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.BestBidAsk;
import com.argus.feedgateway.domain.Instrument;
import com.argus.feedgateway.domain.Trade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProtobufMappersTest {

  private static final Instrument BTCUSDT = new Instrument("BINANCE", "BTCUSDT");

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
    var message =
        ProtobufMappers.trade(
            new Trade()
                .instrument(BTCUSDT)
                .tradeId(1)
                .price(1, 0)
                .quantity(1, 0)
                .aggressorSide(side)
                .exchangeTimestamp(1)
                .receiveTimestamp(1),
            1);

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
}
