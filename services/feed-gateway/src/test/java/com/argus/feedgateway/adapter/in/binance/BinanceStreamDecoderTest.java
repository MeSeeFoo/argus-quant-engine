package com.argus.feedgateway.adapter.in.binance;

import static org.assertj.core.api.Assertions.assertThat;

import com.argus.feedgateway.adapter.in.binance.BinanceFrames.Trade;
import com.argus.feedgateway.adapter.in.binance.sbe.DepthSnapshotStreamEventEncoder;
import com.argus.feedgateway.adapter.in.binance.sbe.MessageHeaderEncoder;
import com.argus.feedgateway.application.MarketDataCounters;
import com.argus.feedgateway.application.MarketDataNormaliser;
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

  private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024]);
  private final BinanceFrames frames = new BinanceFrames();
  private final RecordingPublisher publisher = new RecordingPublisher();
  private final MarketDataCounters counters = new MarketDataCounters();
  private final Instruments instruments = new Instruments(List.of(BTCUSDT));
  private final BinanceStreamDecoder decoder =
      new BinanceStreamDecoder(
          instruments, new MarketDataNormaliser(instruments, publisher, counters), counters);

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
  void ignoresFramesShorterThanAHeader() {
    decoder.onFrame(buffer, 0, MessageHeaderEncoder.ENCODED_LENGTH - 1, RECEIVED);

    assertThat(counters.unsupportedMessages()).isEqualTo(1);
  }

  @Test
  void ignoresTruncatedFrames() {
    int length = frames.trades(buffer, 0, "BTCUSDT", 1, 1, -2, -8, new Trade(1, 1, 1, false));
    decoder.onFrame(buffer, 0, length - 4, RECEIVED);
    length = frames.bestBidAsk(buffer, 0, "BTCUSDT", 1, 1, -2, -2, 1, 1, 2, 1);
    decoder.onFrame(buffer, 0, length - 20, RECEIVED);

    assertThat(publisher.trades).isEmpty();
    assertThat(publisher.bestBidAsks).isEmpty();
    assertThat(counters.unsupportedMessages()).isEqualTo(2);
  }
}
