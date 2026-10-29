package com.argus.architecture.fixtures.inboundusesoutputport.adapter.in;

import com.argus.architecture.fixtures.inboundusesoutputport.application.port.out.OrderRepository;

public class OrderController {
  @SuppressWarnings("UnusedVariable") // only here to create the forbidden dependency
  private final OrderRepository repository;

  public OrderController(OrderRepository repository) {
    this.repository = repository;
  }
}
