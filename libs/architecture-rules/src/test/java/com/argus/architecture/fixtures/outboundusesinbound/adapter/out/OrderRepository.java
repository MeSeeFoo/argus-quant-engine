package com.argus.architecture.fixtures.outboundusesinbound.adapter.out;

import com.argus.architecture.fixtures.outboundusesinbound.adapter.in.OrderController;

public class OrderRepository {
  @SuppressWarnings("UnusedVariable") // only here to create the forbidden dependency
  private final OrderController controller = new OrderController();
}
