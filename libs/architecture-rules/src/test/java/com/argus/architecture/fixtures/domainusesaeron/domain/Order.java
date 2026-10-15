package com.argus.architecture.fixtures.domainusesaeron.domain;

import io.aeron.Publication;

public record Order(Publication publication) {}
