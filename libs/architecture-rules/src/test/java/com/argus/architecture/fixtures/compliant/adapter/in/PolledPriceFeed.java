package com.argus.architecture.fixtures.compliant.adapter.in;

import com.argus.architecture.fixtures.compliant.application.port.in.PriceSource;

public class PolledPriceFeed implements PriceSource {
  @Override
  public int poll(int limit) {
    return 0;
  }
}
