package com.argus.architecture;

import static com.argus.architecture.HexagonalArchitecture.APPLICATION_MUST_NOT_DEPEND_ON_ADAPTERS;
import static com.argus.architecture.HexagonalArchitecture.DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS;
import static com.argus.architecture.HexagonalArchitecture.DOMAIN_MUST_NOT_DEPEND_ON_OTHER_LAYERS;
import static com.argus.architecture.HexagonalArchitecture.INBOUND_ADAPTERS_MUST_NOT_DEPEND_ON_OUTBOUND_ADAPTERS;
import static com.argus.architecture.HexagonalArchitecture.ONLY_CONFIG_MAY_DEPEND_ON_ADAPTERS;
import static com.argus.architecture.HexagonalArchitecture.OUTBOUND_ADAPTERS_MUST_NOT_DEPEND_ON_INBOUND_ADAPTERS;
import static com.argus.architecture.HexagonalArchitecture.PACKAGES_MUST_BE_FREE_OF_CYCLES;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Proves that every rule fails when it should, and only then. Each package under {@code fixtures}
 * is a tiny service; all but {@code compliant} contain a deliberate violation.
 */
class HexagonalArchitectureTest {

  private static final String FIXTURES = "com.argus.architecture.fixtures.";

  static Stream<Arguments> fixtures() {
    return Stream.of(
        arguments("compliant", Set.of()),
        arguments("domainusesspring", Set.of(DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS)),
        arguments("domainuseskafka", Set.of(DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS)),
        arguments("domainusesaeron", Set.of(DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS)),
        arguments("domainusesapplication", Set.of(DOMAIN_MUST_NOT_DEPEND_ON_OTHER_LAYERS)),
        arguments(
            "domainusesadapter",
            Set.of(DOMAIN_MUST_NOT_DEPEND_ON_OTHER_LAYERS, ONLY_CONFIG_MAY_DEPEND_ON_ADAPTERS)),
        arguments(
            "applicationusesadapter",
            Set.of(APPLICATION_MUST_NOT_DEPEND_ON_ADAPTERS, ONLY_CONFIG_MAY_DEPEND_ON_ADAPTERS)),
        arguments("applicationusesconfig", Set.of(APPLICATION_MUST_NOT_DEPEND_ON_ADAPTERS)),
        arguments(
            "inboundusesoutbound", Set.of(INBOUND_ADAPTERS_MUST_NOT_DEPEND_ON_OUTBOUND_ADAPTERS)),
        arguments(
            "outboundusesinbound", Set.of(OUTBOUND_ADAPTERS_MUST_NOT_DEPEND_ON_INBOUND_ADAPTERS)),
        arguments("cycle", Set.of(PACKAGES_MUST_BE_FREE_OF_CYCLES)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void fixtureViolatesExactlyTheExpectedRules(String fixture, Set<ArchRule> expected) {
    JavaClasses classes = importFixture(fixture);

    Set<ArchRule> violated =
        HexagonalArchitecture.rules().stream()
            .filter(rule -> rule.evaluate(classes).hasViolation())
            .collect(toSet());

    assertEquals(descriptions(expected), descriptions(violated));
  }

  @Test
  void everyRuleIsProvenByAtLeastOneFixture() {
    Set<ArchRule> proven =
        fixtures()
            .flatMap(fixture -> ((Set<?>) fixture.get()[1]).stream())
            .map(ArchRule.class::cast)
            .collect(toSet());

    assertEquals(descriptions(Set.copyOf(HexagonalArchitecture.rules())), descriptions(proven));
  }

  @Test
  void aViolationFailsTheTestAndNamesTheOffendingClass() {
    JavaClasses classes = importFixture("domainusesspring");

    AssertionError failure =
        assertThrows(
            AssertionError.class, () -> DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS.check(classes));

    assertTrue(failure.getMessage().contains("domainusesspring.domain.Order"));
    assertTrue(failure.getMessage().contains("org.springframework.util.Assert"));
  }

  @Test
  void missingLayersAreNotAnError() {
    JavaClasses onlyDomain = importFixture("compliant.domain");

    HexagonalArchitecture.rules().forEach(rule -> rule.check(onlyDomain));
  }

  private static JavaClasses importFixture(String fixture) {
    JavaClasses classes = new ClassFileImporter().importPackages(FIXTURES + fixture);
    assertFalse(classes.isEmpty(), () -> "No classes found for fixture " + fixture);
    return classes;
  }

  private static Set<String> descriptions(Set<ArchRule> rules) {
    return rules.stream().map(ArchRule::getDescription).collect(toSet());
  }
}
