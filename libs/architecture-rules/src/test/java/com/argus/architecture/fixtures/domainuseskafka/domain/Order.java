package com.argus.architecture.fixtures.domainuseskafka.domain;

import org.apache.kafka.clients.producer.ProducerRecord;

public record Order(ProducerRecord<String, String> record) {}
