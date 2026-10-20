package com.argus.feedgateway.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BookUpdateFilterTest {

  private final BookUpdateFilter filter = new BookUpdateFilter(2);

  @Test
  void acceptsOnlyNewerUpdates() {
    assertThat(filter.accept(0, 100)).isTrue();
    assertThat(filter.accept(0, 101)).isTrue();
    assertThat(filter.accept(0, 101)).as("repeated").isFalse();
    assertThat(filter.accept(0, 99)).as("older").isFalse();
    assertThat(filter.accept(0, 150)).as("a jump forward is fine").isTrue();
  }

  @Test
  void tracksEachInstrumentOnItsOwn() {
    assertThat(filter.accept(0, 100)).isTrue();
    assertThat(filter.accept(1, 5)).isTrue();
    assertThat(filter.accept(1, 6)).isTrue();
    assertThat(filter.accept(0, 99)).isFalse();
  }
}
