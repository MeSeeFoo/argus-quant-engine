package com.argus.architecture.fixtures.rootusesadapter;

import com.argus.architecture.fixtures.rootusesadapter.adapter.out.OrderRepository;

public class Main {
  @SuppressWarnings("UnusedVariable") // only here to create the forbidden dependency
  private final OrderRepository repository = new OrderRepository();
}
