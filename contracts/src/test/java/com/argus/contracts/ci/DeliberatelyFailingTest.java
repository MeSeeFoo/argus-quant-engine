package com.argus.contracts.ci;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class DeliberatelyFailingTest {

  @Test
  void failsOnPurpose() {
    fail("Deliberate failure to check that CI fails the pull request");
  }
}
