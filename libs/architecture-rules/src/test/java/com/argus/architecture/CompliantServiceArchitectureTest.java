package com.argus.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

/**
 * Applies the rules exactly as a service does (see the module README), to a fixture that follows
 * them. Run by the ArchUnit JUnit engine as part of {@code ./mvnw verify}.
 */
@AnalyzeClasses(packages = "com.argus.architecture.fixtures.compliant")
class CompliantServiceArchitectureTest {

  @ArchTest static final ArchTests hexagonal = ArchTests.in(HexagonalArchitecture.class);
}
