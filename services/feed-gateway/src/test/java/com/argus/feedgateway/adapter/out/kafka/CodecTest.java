package com.argus.feedgateway.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.argus.feedgateway.adapter.out.kafka.mapping.ProtobufMappers;
import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.BestBidAsk;
import com.argus.feedgateway.domain.Instrument;
import com.argus.feedgateway.domain.Instruments;
import com.argus.feedgateway.domain.Trade;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/** What the sender thread does: read the slot the gateway agent wrote, then map it. */
class CodecTest {

  private static final Instruments INSTRUMENTS =
      new Instruments(
          List.of(new Instrument("BINANCE", "BTCUSDT"), new Instrument("BINANCE", "ETHUSDT")));
  // As the relay passes them: from the shard's instruments, with their id, which a slot stores
  private static final Instrument BTCUSDT = INSTRUMENTS.get(0);
  private static final Instrument ETHUSDT = INSTRUMENTS.get(1);

  @Test
  void aTradeSurvivesTheSlotAndTheMapping() {
    var buffer = new UnsafeBuffer(new byte[TradeCodec.LENGTH]);
    Trade written =
        new Trade()
            .instrument(BTCUSDT)
            .tradeId(42)
            .price(345_678, -2)
            .quantity(15, -3)
            .aggressorSide(AggressorSide.SELL)
            .exchangeTimestamp(1)
            .receiveTimestamp(1);
    TradeCodec.write(buffer, 0, 9, written);

    Trade read = new Trade();
    TradeCodec.read(buffer, 0, INSTRUMENTS, read);
    var message = ProtobufMappers.trade(read, buffer.getLong(0));

    assertThat(message).isEqualTo(ProtobufMappers.trade(written, 9));
    assertThat(message.getPrice()).as("canonical scale").isEqualTo(345_678_000_000L);
    assertThat(message.getQuantity()).isEqualTo(1_500_000L);
  }

  @Test
  void aTopOfBookSurvivesTheSlotAndTheMapping() {
    var buffer = new UnsafeBuffer(new byte[BestBidAskCodec.LENGTH]);
    BestBidAsk written =
        new BestBidAsk()
            .instrument(ETHUSDT)
            .bookUpdateId(5)
            .bid(1, 0, 2, 0)
            .ask(3, 0, 4, 0)
            .exchangeTimestamp(31)
            .receiveTimestamp(32);
    BestBidAskCodec.write(buffer, 0, 10, written);

    BestBidAsk read = new BestBidAsk();
    BestBidAskCodec.read(buffer, 0, INSTRUMENTS, read);
    var message = ProtobufMappers.bestBidAsk(read, buffer.getLong(0));

    assertThat(message).isEqualTo(ProtobufMappers.bestBidAsk(written, 10));
  }
}
