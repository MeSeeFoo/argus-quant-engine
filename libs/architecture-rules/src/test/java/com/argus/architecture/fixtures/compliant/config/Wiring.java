package com.argus.architecture.fixtures.compliant.config;

import com.argus.architecture.fixtures.compliant.adapter.in.PriceListener;
import com.argus.architecture.fixtures.compliant.adapter.out.LoggingPricePublisher;
import com.argus.architecture.fixtures.compliant.application.PublishPrice;

public final class Wiring {
  public static PriceListener priceListener() {
    return new PriceListener(new PublishPrice(new LoggingPricePublisher()));
  }

  private Wiring() {}
}
