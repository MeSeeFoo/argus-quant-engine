package com.argus.architecture.fixtures.compliant.config;

import com.argus.architecture.fixtures.compliant.adapter.in.PriceListener;
import com.argus.architecture.fixtures.compliant.adapter.out.LoggingPricePublisher;
import com.argus.architecture.fixtures.compliant.application.PricePublishing;

public final class Wiring {
  public static PriceListener priceListener() {
    return new PriceListener(new PricePublishing(new LoggingPricePublisher()));
  }

  private Wiring() {}
}
