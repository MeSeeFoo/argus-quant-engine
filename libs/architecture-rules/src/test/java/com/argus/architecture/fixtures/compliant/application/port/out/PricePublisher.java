package com.argus.architecture.fixtures.compliant.application.port.out;

import com.argus.architecture.fixtures.compliant.domain.Price;

public interface PricePublisher {
  void publish(Price price);
}
