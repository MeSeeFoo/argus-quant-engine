package com.argus.architecture.fixtures.outboundusesinputport.adapter.out;

import com.argus.architecture.fixtures.outboundusesinputport.application.port.in.PlaceOrder;

public class OrderRepository {
  @SuppressWarnings("UnusedVariable") // only here to create the forbidden dependency
  private final PlaceOrder placeOrder;

  public OrderRepository(PlaceOrder placeOrder) {
    this.placeOrder = placeOrder;
  }
}
