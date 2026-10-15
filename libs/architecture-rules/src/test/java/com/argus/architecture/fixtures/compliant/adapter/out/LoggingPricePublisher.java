package com.argus.architecture.fixtures.compliant.adapter.out;

import com.argus.architecture.fixtures.compliant.application.PricePublisher;
import com.argus.architecture.fixtures.compliant.domain.Price;

public class LoggingPricePublisher implements PricePublisher {
  @Override
  public void publish(Price price) {
    System.out.println(price);
  }
}
