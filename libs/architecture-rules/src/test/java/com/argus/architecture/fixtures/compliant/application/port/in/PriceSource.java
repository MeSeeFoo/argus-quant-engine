package com.argus.architecture.fixtures.compliant.application.port.in;

public interface PriceSource {
  int poll(int limit);
}
