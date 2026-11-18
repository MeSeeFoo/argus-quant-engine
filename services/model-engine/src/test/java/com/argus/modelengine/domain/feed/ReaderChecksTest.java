package com.argus.modelengine.domain.feed;

import static com.argus.modelengine.domain.feed.SequenceGapDetector.NEW_SESSION;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReaderChecksTest {

  private final Deduplicator deduplicator = new Deduplicator(2);
  private final SequenceGapDetector gaps = new SequenceGapDetector();

  @Test
  void acceptsNewerTrades() {
    assertThat(deduplicator.accept(0, 0)).isTrue();
    assertThat(deduplicator.accept(0, 1)).isTrue();
    assertThat(deduplicator.accept(0, 5)).isTrue();
  }

  @Test
  void dropsDuplicateAndOldTrades() {
    deduplicator.accept(0, 10);

    assertThat(deduplicator.accept(0, 10)).as("duplicate").isFalse();
    assertThat(deduplicator.accept(0, 9)).as("old").isFalse();
  }

  @Test
  void keepsTheLastTradeOfEachInstrument() {
    deduplicator.accept(0, 10);

    assertThat(deduplicator.accept(1, 5)).isTrue();
  }

  @Test
  void consecutiveSequencesAreNotAGap() {
    assertThat(gaps.onMessage(1, 1)).isEqualTo(NEW_SESSION);
    assertThat(gaps.onMessage(1, 2)).isZero();
    assertThat(gaps.onMessage(1, 3)).isZero();
  }

  @Test
  void findsTheMessagesOfAGap() {
    gaps.onMessage(1, 1);

    assertThat(gaps.onMessage(1, 5)).isEqualTo(3);
  }

  @Test
  void anOldOrRepeatedSequenceIsNotAGap() {
    gaps.onMessage(1, 5);

    assertThat(gaps.onMessage(1, 5)).isZero();
    assertThat(gaps.onMessage(1, 3)).isZero();
    assertThat(gaps.onMessage(1, 6)).isZero();
  }

  @Test
  void joiningAStreamLateIsNotAGap() {
    assertThat(gaps.onMessage(1, 1_000)).isEqualTo(NEW_SESSION);
  }

  @Test
  void aPublisherRestartIsANewSessionNotAGap() {
    gaps.onMessage(1, 1_000);

    assertThat(gaps.onMessage(2, 1)).isEqualTo(NEW_SESSION);
  }

  @Test
  void aNewSessionWithAHigherSequenceIsNotAGap() {
    gaps.onMessage(1, 10);

    assertThat(gaps.onMessage(2, 500)).isEqualTo(NEW_SESSION);
  }

  @Test
  void aHeartbeatWithTheLastSequenceIsNotAGap() {
    gaps.onMessage(1, 7);

    assertThat(gaps.onHeartbeat(1, 7)).isZero();
    assertThat(gaps.onMessage(1, 8)).isZero();
  }

  @Test
  void aHeartbeatRevealsMessagesLostBeforeIt() {
    gaps.onMessage(1, 7);

    assertThat(gaps.onHeartbeat(1, 10)).isEqualTo(3);
    assertThat(gaps.onHeartbeat(1, 10)).as("the same heartbeat").isZero();
  }

  @Test
  void aHeartbeatCanStartASession() {
    assertThat(gaps.onHeartbeat(1, 0)).isEqualTo(NEW_SESSION);
    assertThat(gaps.onMessage(1, 1)).isZero();
  }
}
