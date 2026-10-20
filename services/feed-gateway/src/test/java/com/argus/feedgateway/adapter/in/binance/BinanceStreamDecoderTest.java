package com.argus.feedgateway.adapter.in.binance;

import static org.assertj.core.api.Assertions.assertThat;

import com.argus.feedgateway.adapter.in.binance.BinanceFrames.Trade;
import com.argus.feedgateway.adapter.in.binance.sbe.DepthSnapshotStreamEventEncoder;
import com.argus.feedgateway.adapter.in.binance.sbe.GroupSizeEncodingDecoder;
import com.argus.feedgateway.adapter.in.binance.sbe.MessageHeaderEncoder;
import com.argus.feedgateway.adapter.in.binance.sbe.TradesStreamEventDecoder;
import com.argus.feedgateway.adapter.in.binance.sbe.TradesStreamEventDecoder.TradesDecoder;
import com.argus.feedgateway.application.MarketDataCounters;
import com.argus.feedgateway.application.MarketDataRelay;
import com.argus.feedgateway.application.RecordingPublisher;
import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.Instrument;
import com.argus.feedgateway.domain.Instruments;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class BinanceStreamDecoderTest {

  private static final Instrument BTCUSDT = new Instrument("BINANCE", "BTCUSDT");
  private static final long RECEIVED = 1_760_000_000_123_456_789L;
  private static final int TRADES_GROUP_OFFSET =
      MessageHeaderEncoder.ENCODED_LENGTH + TradesStreamEventDecoder.BLOCK_LENGTH;
  private static final int GROUP_HEADER = GroupSizeEncodingDecoder.ENCODED_LENGTH;

  private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024]);
  private final BinanceFrames frames = new BinanceFrames();
  private final RecordingPublisher publisher = new RecordingPublisher();
  private final BinanceCounters counters = new BinanceCounters();
  private final MarketDataCounters relayCounters = new MarketDataCounters();
  private final Instruments instruments = new Instruments(List.of(BTCUSDT));
  private final BinanceStreamDecoder decoder =
      new BinanceStreamDecoder(
          instruments,
          new MarketDataRelay(instruments, publisher, relayCounters, 1_000_000_000L),
          counters);

  @Test
  void publishesEveryTradeOfAnEvent() {
    int offset = 16;
    int length =
        frames.trades(
            buffer,
            offset,
            "BTCUSDT",
            1_760_000_000_000_002L,
            1_760_000_000_000_001L,
            -2,
            -8,
            new Trade(1001, 6421050, 12_000_000, true),
            new Trade(1002, 6421100, 1, false));

    decoder.onFrame(buffer, offset, length, RECEIVED);

    long executed = 1_760_000_000_000_001_000L;
    assertThat(publisher.trades)
        .containsExactly(
            new RecordingPublisher.Trade(
                BTCUSDT,
                1001,
                6_421_050_000_000L,
                12_000_000,
                executed,
                RECEIVED,
                AggressorSide.SELL),
            new RecordingPublisher.Trade(
                BTCUSDT, 1002, 6_421_100_000_000L, 1, executed, RECEIVED, AggressorSide.BUY));
  }

  @Test
  void publishesTopOfBookWithTheVenueEventTime() {
    int length =
        frames.bestBidAsk(
            buffer, 0, "BTCUSDT", 1_760_000_000_000_003L, 77, -2, -5, 6421050, 3, 6421051, 120);

    decoder.onFrame(buffer, 0, length, RECEIVED);

    assertThat(publisher.bestBidAsks)
        .containsExactly(
            new RecordingPublisher.BestBidAsk(
                BTCUSDT,
                77,
                6_421_050_000_000L,
                3_000L,
                6_421_051_000_000L,
                120_000L,
                1_760_000_000_000_003_000L,
                RECEIVED));
  }

  @Test
  void ignoresSymbolsOutsideTheShard() {
    int length = frames.trades(buffer, 0, "ETHUSDT", 1, 1, -2, -8, new Trade(1, 1, 1, false));
    decoder.onFrame(buffer, 0, length, RECEIVED);
    length =
        frames.bestBidAsk(
            buffer, 0, "BTCUSDTLONGERTHANANYSYMBOLOFTHESHARD", 1, 1, 0, 0, 1, 1, 2, 1);
    decoder.onFrame(buffer, 0, length, RECEIVED);

    assertThat(publisher.trades).isEmpty();
    assertThat(publisher.bestBidAsks).isEmpty();
    assertThat(counters.unknownSymbols()).isEqualTo(2);
  }

  @Test
  void ignoresMessagesItDoesNotHandle() {
    DepthSnapshotStreamEventEncoder depth = new DepthSnapshotStreamEventEncoder();
    depth.wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder());
    depth.bidsCount(0);
    depth.asksCount(0);
    depth.symbol("BTCUSDT");

    decoder.onFrame(buffer, 0, MessageHeaderEncoder.ENCODED_LENGTH + depth.encodedLength(), 1);

    assertThat(counters.unsupportedMessages()).isEqualTo(1);
  }

  @Test
  void ignoresOtherSchemas() {
    int length = frames.trades(buffer, 0, "BTCUSDT", 1, 1, -2, -8, new Trade(1, 1, 1, false));
    new MessageHeaderEncoder().wrap(buffer, 0).schemaId(99);

    decoder.onFrame(buffer, 0, length, RECEIVED);

    assertThat(publisher.trades).isEmpty();
    assertThat(counters.unsupportedMessages()).isEqualTo(1);
  }

  @Test
  void dropsOnlyTheTradesWithUnrepresentableValues() {
    long tooLarge = 100_000_000_000L; // x 10^8 does not fit in int64
    int length =
        frames.trades(
            buffer,
            0,
            "BTCUSDT",
            1,
            1,
            0,
            -8,
            new Trade(1, 64210, 1, false),
            new Trade(2, tooLarge, 1, false),
            new Trade(3, 64211, 1, false));

    decoder.onFrame(buffer, 0, length, RECEIVED);

    assertThat(publisher.trades)
        .extracting(RecordingPublisher.Trade::tradeId)
        .containsExactly(1L, 3L);
    assertThat(relayCounters.unrepresentableValues()).isEqualTo(1);
  }

  @Test
  void rejectsTheWholeEventWhenAnAggressorIsInvalid() {
    int length =
        frames.trades(
            buffer,
            0,
            "BTCUSDT",
            1,
            1,
            -2,
            -8,
            new Trade(1, 1, 1, false),
            new Trade(2, 1, 1, true));
    int secondEntry = TRADES_GROUP_OFFSET + GROUP_HEADER + TradesDecoder.sbeBlockLength();
    buffer.putByte(secondEntry + TradesDecoder.isBuyerMakerEncodingOffset(), (byte) 255);

    decoder.onFrame(buffer, 0, length, RECEIVED);

    assertThat(publisher.trades).isEmpty();
    assertThat(counters.unsupportedMessages()).isEqualTo(1);
  }

  @Test
  void rejectsLengthsFromTheWireThatDoNotFitTheFrame() {
    int length = frames.trades(buffer, 0, "BTCUSDT", 1, 1, -2, -8, new Trade(1, 1, 1, false));
    // Would make the decoder walk two billion entries
    buffer.putInt(TRADES_GROUP_OFFSET + 2, Integer.MAX_VALUE);
    decoder.onFrame(buffer, 0, length, RECEIVED);
    buffer.putInt(TRADES_GROUP_OFFSET + 2, -1);
    decoder.onFrame(buffer, 0, length, RECEIVED);

    length = frames.trades(buffer, 0, "BTCUSDT", 1, 1, -2, -8, new Trade(1, 1, 1, false));
    buffer.putShort(TRADES_GROUP_OFFSET, (short) 1);
    decoder.onFrame(buffer, 0, length, RECEIVED);

    length = frames.trades(buffer, 0, "BTCUSDT", 1, 1, -2, -8, new Trade(1, 1, 1, false));
    new MessageHeaderEncoder().wrap(buffer, 0).blockLength(4);
    decoder.onFrame(buffer, 0, length, RECEIVED);

    length = frames.bestBidAsk(buffer, 0, "BTCUSDT", 1, 1, -2, -2, 1, 1, 2, 1);
    new MessageHeaderEncoder().wrap(buffer, 0).blockLength(4);
    decoder.onFrame(buffer, 0, length, RECEIVED);

    assertThat(publisher.trades).isEmpty();
    assertThat(publisher.bestBidAsks).isEmpty();
    assertThat(counters.unsupportedMessages()).isEqualTo(5);
  }

  @Test
  void ignoresFramesShorterThanAHeader() {
    decoder.onFrame(buffer, 0, MessageHeaderEncoder.ENCODED_LENGTH - 1, RECEIVED);

    assertThat(counters.unsupportedMessages()).isEqualTo(1);
  }

  @Test
  void ignoresTruncatedFrames() {
    int length = frames.trades(buffer, 0, "BTCUSDT", 1, 1, -2, -8, new Trade(1, 1, 1, false));
    decoder.onFrame(buffer, 0, length - 4, RECEIVED);
    decoder.onFrame(buffer, 0, length - 12, RECEIVED);
    length = frames.bestBidAsk(buffer, 0, "BTCUSDT", 1, 1, -2, -2, 1, 1, 2, 1);
    decoder.onFrame(buffer, 0, length - 2, RECEIVED);
    decoder.onFrame(buffer, 0, length - 20, RECEIVED);

    assertThat(publisher.trades).isEmpty();
    assertThat(publisher.bestBidAsks).isEmpty();
    assertThat(counters.unsupportedMessages()).isEqualTo(4);
  }
}
