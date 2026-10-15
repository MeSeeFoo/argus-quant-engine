package com.argus.architecture.fixtures.domainusesadapter.domain;

import com.argus.architecture.fixtures.domainusesadapter.adapter.out.OrderRepository;

public record Order(OrderRepository repository) {}
