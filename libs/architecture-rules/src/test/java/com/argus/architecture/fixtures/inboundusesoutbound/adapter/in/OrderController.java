package com.argus.architecture.fixtures.inboundusesoutbound.adapter.in;

import com.argus.architecture.fixtures.inboundusesoutbound.adapter.out.OrderRepository;

public class OrderController {
  private final OrderRepository repository = new OrderRepository();
}
