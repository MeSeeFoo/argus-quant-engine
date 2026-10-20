package com.argus.architecture.fixtures.adapterusesapplicationservice.adapter.in;

import com.argus.architecture.fixtures.adapterusesapplicationservice.application.PlaceOrder;

public class OrderController {
  @SuppressWarnings("UnusedVariable") // only here to create the forbidden dependency
  private final PlaceOrder placeOrder = new PlaceOrder();
}
