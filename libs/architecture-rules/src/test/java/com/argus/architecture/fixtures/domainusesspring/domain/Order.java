package com.argus.architecture.fixtures.domainusesspring.domain;

import org.springframework.util.Assert;

public record Order(String symbol) {
  public Order {
    Assert.hasText(symbol, "symbol");
  }
}
