package com.argus.architecture.fixtures.cycle.domain.order;

import com.argus.architecture.fixtures.cycle.domain.fill.Fill;
import java.util.List;

public record Order(List<Fill> fills) {}
