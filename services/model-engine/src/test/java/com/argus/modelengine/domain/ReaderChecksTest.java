package com.argus.modelengine.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReaderChecksTest {

  private final TradeDeduplicator deduplicator = new TradeDeduplicator(2);
  private final SequenceGapCounter gaps = new SequenceGapCounter();

  @Test
  void acceptsNewerTrades() {
    assertThat(deduplicator.accept(0, 0)).isTrue();
    assertThat(deduplicator.accept(0, 1)).isTrue();
    assertThat(deduplicator.accept(0, 5)).isTrue();
    assertThat(deduplicator.droppedTrades()).isZero();
  }

  @Test
  void dropsAndCountsDuplicateAndOldTrades() {
    deduplicator.accept(0, 10);

    assertThat(deduplicator.accept(0, 10)).as("duplicate").isFalse();
    assertThat(deduplicator.accept(0, 9)).as("old").isFalse();
    assertThat(deduplicator.droppedTrades()).isEqualTo(2);
  }

  @Test
  void keepsTheLastTradeOfEachInstrument() {
    deduplicator.accept(0, 10);

    assertThat(deduplicator.accept(1, 5)).isTrue();
  }

  @Test
  void consecutiveSequencesAreNotAGap() {
    gaps.onMessage(1, 1);
    gaps.onMessage(1, 2);
    gaps.onMessage(1, 3);

    assertThat(gaps.missedMessages()).isZero();
    assertThat(gaps.sessions()).isEqualTo(1);
  }

  @Test
  void countsTheMessagesOfAGap() {
    gaps.onMessage(1, 1);
    gaps.onMessage(1, 5);

    assertThat(gaps.missedMessages()).isEqualTo(3);
  }

  @Test
  void joiningAStreamLateIsNotAGap() {
    gaps.onMessage(1, 1_000);

    assertThat(gaps.missedMessages()).isZero();
  }

  @Test
  void aPublisherRestartIsNotAGap() {
    gaps.onMessage(1, 1_000);
    gaps.onMessage(2, 1);

    assertThat(gaps.missedMessages()).isZero();
    assertThat(gaps.sessions()).isEqualTo(2);
  }

  @Test
  void aNewSessionWithAHigherSequenceIsNotAGap() {
    gaps.onMessage(1, 10);
    gaps.onMessage(2, 500);

    assertThat(gaps.missedMessages()).isZero();
  }

  @Test
  void aHeartbeatWithTheLastSequenceIsNotAGap() {
    gaps.onMessage(1, 7);
    gaps.onHeartbeat(1, 7);
    gaps.onMessage(1, 8);

    assertThat(gaps.missedMessages()).isZero();
  }

  @Test
  void aHeartbeatRevealsMessagesLostBeforeIt() {
    gaps.onMessage(1, 7);
    gaps.onHeartbeat(1, 10);

    assertThat(gaps.missedMessages()).isEqualTo(3);
  }

  @Test
  void aHeartbeatCanStartASession() {
    gaps.onHeartbeat(1, 0);
    gaps.onMessage(1, 1);

    assertThat(gaps.missedMessages()).isZero();
    assertThat(gaps.sessions()).isEqualTo(1);
  }
}
