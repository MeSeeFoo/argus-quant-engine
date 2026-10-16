package com.argus.architecture.fixtures.applicationusesadapter.application;

import com.argus.architecture.fixtures.applicationusesadapter.adapter.out.OrderRepository;

public class PlaceOrder {
  @SuppressWarnings("UnusedVariable") // only here to create the forbidden dependency
  private final OrderRepository repository = new OrderRepository();
}
