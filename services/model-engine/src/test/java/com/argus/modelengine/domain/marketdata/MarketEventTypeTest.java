package com.argus.modelengine.domain.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MarketEventTypeTest {

  @Test
  void everyKindHasItsOwnIndexBelowTheCount() {
    Set<Integer> indexes = new HashSet<>();
    for (MarketEventType type : MarketEventType.values()) {
      assertThat(type.index()).isBetween(0, MarketEventType.COUNT - 1);
      indexes.add(type.index());
    }

    assertThat(indexes).hasSize(MarketEventType.values().length);
    assertThat(MarketEventType.COUNT).isEqualTo(MarketEventType.values().length);
  }
}
