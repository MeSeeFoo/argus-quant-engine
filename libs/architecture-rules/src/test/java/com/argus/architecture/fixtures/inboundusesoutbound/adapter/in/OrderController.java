package com.argus.architecture.fixtures.inboundusesoutbound.adapter.in;

import com.argus.architecture.fixtures.inboundusesoutbound.adapter.out.OrderRepository;

public class OrderController {
  @SuppressWarnings("UnusedVariable") // only here to create the forbidden dependency
  private final OrderRepository repository = new OrderRepository();
}
