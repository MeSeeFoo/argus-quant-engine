package com.argus.architecture.fixtures.compliant.application;

import com.argus.architecture.fixtures.compliant.application.port.in.PublishPrice;
import com.argus.architecture.fixtures.compliant.application.port.out.PricePublisher;
import com.argus.architecture.fixtures.compliant.domain.Price;

public class PricePublishing implements PublishPrice {
  private final PricePublisher publisher;

  public PricePublishing(PricePublisher publisher) {
    this.publisher = publisher;
  }

  @Override
  public void handle(long mantissa) {
    publisher.publish(new Price(mantissa));
  }
}
