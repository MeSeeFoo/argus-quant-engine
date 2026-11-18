package com.argus.modelengine.application;

import static com.argus.modelengine.application.TestMarketData.trade;
import static org.assertj.core.api.Assertions.assertThat;

import com.argus.modelengine.application.TestMarketData.SampleBestBidAsk;
import com.argus.modelengine.domain.feed.Deduplicator;
import com.argus.modelengine.domain.feed.SequenceGapDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class MarketDataEventCheckerTest {

  private static final int SESSION = 5;

  private final MarketDataCounters counters = new MarketDataCounters();
  private final MarketDataEventChecker checker =
      new MarketDataEventChecker(
          new Deduplicator(2), new Deduplicator(2), new SequenceGapDetector(), counters);

  @Test
  void logsAGap(CapturedOutput output) {
    checker.onTrade(SESSION, trade(0, 1, 1000));
    checker.onTrade(SESSION, trade(0, 5, 1001));

    assertThat(counters.missedMessages()).isEqualTo(3);
    assertThat(output).contains("Messages from the gateway were lost").contains("(3 so far");
  }

  @Test
  void acceptsNewTradesAndTopOfBookUpdates() {
    assertThat(checker.onTrade(SESSION, trade(0, 1, 1000))).isTrue();
    assertThat(checker.onBestBidAsk(SESSION, book(0, 2, 77))).isTrue();
  }

  @Test
  void dropsAndCountsDuplicateAndOldTrades() {
    checker.onTrade(SESSION, trade(0, 1, 1000));

    assertThat(checker.onTrade(SESSION, trade(0, 2, 1000))).isFalse();
    assertThat(checker.onTrade(SESSION, trade(0, 3, 999))).isFalse();
    assertThat(counters.duplicateTrades()).isEqualTo(2);
    assertThat(counters.duplicateBestBidAsks()).isZero();
  }

  @Test
  void dropsAndCountsDuplicateAndOldTopOfBookUpdates() {
    checker.onBestBidAsk(SESSION, book(0, 1, 77));

    assertThat(checker.onBestBidAsk(SESSION, book(0, 2, 77))).isFalse();
    assertThat(checker.onBestBidAsk(SESSION, book(0, 3, 76))).isFalse();
    assertThat(checker.onBestBidAsk(SESSION, book(0, 4, 78))).isTrue();
    assertThat(counters.duplicateBestBidAsks()).isEqualTo(2);
    assertThat(counters.duplicateTrades()).isZero();
  }

  @Test
  void idsAreComparedPerInstrument() {
    checker.onTrade(SESSION, trade(0, 1, 1000));
    checker.onBestBidAsk(SESSION, book(0, 2, 77));

    assertThat(checker.onTrade(SESSION, trade(1, 3, 5))).isTrue();
    assertThat(checker.onBestBidAsk(SESSION, book(1, 4, 5))).isTrue();
  }

  @Test
  void tradesAndTopOfBookUpdatesHaveIdsOfTheirOwn() {
    checker.onTrade(SESSION, trade(0, 1, 1000));

    assertThat(checker.onBestBidAsk(SESSION, book(0, 2, 5))).isTrue();
    assertThat(counters.duplicateTrades()).isZero();
    assertThat(counters.duplicateBestBidAsks()).isZero();
  }

  @Test
  void aDroppedEventStillTakesItsSequence() {
    checker.onTrade(SESSION, trade(0, 1, 1000));
    checker.onTrade(SESSION, trade(0, 2, 1000));
    checker.onTrade(SESSION, trade(0, 3, 1001));

    assertThat(counters.missedMessages()).isZero();
  }

  @Test
  void countsJumpsInTheSequence() {
    checker.onTrade(SESSION, trade(0, 1, 1000));
    checker.onBestBidAsk(SESSION, book(0, 4, 77));

    assertThat(counters.missedMessages()).isEqualTo(2);
  }

  @Test
  void anIgnoredEventIsNotAGap() {
    checker.onTrade(SESSION, trade(0, 1, 1000));
    checker.onIgnored(SESSION, 2);
    checker.onTrade(SESSION, trade(0, 3, 1001));

    assertThat(counters.missedMessages()).isZero();
  }

  @Test
  void aHeartbeatRevealsMessagesLostBeforeIt() {
    checker.onTrade(SESSION, trade(0, 1, 1000));
    checker.onHeartbeat(SESSION, 3);

    assertThat(counters.missedMessages()).isEqualTo(2);
  }

  @Test
  void countsEachPublisherSession() {
    checker.onTrade(SESSION, trade(0, 1, 1000));
    checker.onTrade(SESSION, trade(0, 2, 1001));
    checker.onHeartbeat(SESSION + 1, 50);
    checker.onTrade(SESSION + 1, trade(0, 51, 1002));

    assertThat(counters.publisherSessions()).isEqualTo(2);
    assertThat(counters.missedMessages()).as("a new session is not a gap").isZero();
  }

  private static SampleBestBidAsk book(int instrumentId, long sequence, long bookUpdateId) {
    return new SampleBestBidAsk(instrumentId, sequence, 21, 22, bookUpdateId, 100, 1, 101, 2);
  }
}
