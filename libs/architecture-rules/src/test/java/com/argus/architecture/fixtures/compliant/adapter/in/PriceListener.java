package com.argus.architecture.fixtures.compliant.adapter.in;

import com.argus.architecture.fixtures.compliant.application.port.in.PublishPrice;

public class PriceListener {
  private final PublishPrice useCase;

  public PriceListener(PublishPrice useCase) {
    this.useCase = useCase;
  }

  public void onMessage(long mantissa) {
    useCase.handle(mantissa);
  }
}
