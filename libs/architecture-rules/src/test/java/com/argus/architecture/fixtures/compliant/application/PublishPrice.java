package com.argus.architecture.fixtures.compliant.application;

import com.argus.architecture.fixtures.compliant.domain.Price;

public class PublishPrice {
  private final PricePublisher publisher;

  public PublishPrice(PricePublisher publisher) {
    this.publisher = publisher;
  }

  public void handle(long mantissa) {
    publisher.publish(new Price(mantissa));
  }
}
