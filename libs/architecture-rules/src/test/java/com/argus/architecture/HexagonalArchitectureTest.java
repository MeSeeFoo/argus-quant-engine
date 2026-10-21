package com.argus.architecture;

import static com.argus.architecture.HexagonalArchitecture.ADAPTERS_MUST_USE_THE_APPLICATION_THROUGH_PORTS;
import static com.argus.architecture.HexagonalArchitecture.DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS;
import static com.argus.architecture.HexagonalArchitecture.INBOUND_ADAPTERS_USE_ONLY_INPUT_PORTS;
import static com.argus.architecture.HexagonalArchitecture.INNER_LAYERS_MUST_NOT_DEPEND_ON_CONFIG;
import static com.argus.architecture.HexagonalArchitecture.LAYERS_MUST_RESPECT_THE_HEXAGON;
import static com.argus.architecture.HexagonalArchitecture.OUTBOUND_ADAPTERS_USE_ONLY_OUTPUT_PORTS;
import static com.argus.architecture.HexagonalArchitecture.PACKAGES_MUST_BE_FREE_OF_CYCLES;
import static com.argus.architecture.HexagonalArchitecture.PORTS_MUST_LIVE_IN_PORT_PACKAGES;
import static java.util.Objects.requireNonNull;
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

  /** Fixture package, the rules it violates, and the forbidden dependency the report must name. */
  static Stream<Arguments> fixtures() {
    return Stream.of(
        arguments("compliant", Set.of(), ""),
        arguments(
            "domainusesspring",
            Set.of(DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS),
            "org.springframework"),
        arguments(
            "domainuseskafka", Set.of(DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS), "org.apache.kafka"),
        arguments("domainusesaeron", Set.of(DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS), "io.aeron"),
        arguments(
            "domainusesapplication",
            Set.of(LAYERS_MUST_RESPECT_THE_HEXAGON),
            "application.PlaceOrder"),
        arguments(
            "domainusesadapter",
            Set.of(LAYERS_MUST_RESPECT_THE_HEXAGON),
            "adapter.out.OrderRepository"),
        arguments(
            "applicationusesadapter",
            Set.of(LAYERS_MUST_RESPECT_THE_HEXAGON),
            "adapter.out.OrderRepository"),
        arguments(
            "inboundusesoutbound",
            Set.of(LAYERS_MUST_RESPECT_THE_HEXAGON),
            "adapter.out.OrderRepository"),
        arguments(
            "outboundusesinbound",
            Set.of(LAYERS_MUST_RESPECT_THE_HEXAGON),
            "adapter.in.OrderController"),
        arguments(
            "domainusesconfig", Set.of(INNER_LAYERS_MUST_NOT_DEPEND_ON_CONFIG), "config.Settings"),
        arguments(
            "applicationusesconfig",
            Set.of(INNER_LAYERS_MUST_NOT_DEPEND_ON_CONFIG),
            "config.Settings"),
        arguments(
            "rootusesadapter",
            Set.of(LAYERS_MUST_RESPECT_THE_HEXAGON),
            "adapter.out.OrderRepository"),
        arguments(
            "portoutsideportpackage",
            Set.of(PORTS_MUST_LIVE_IN_PORT_PACKAGES),
            "application.OrderRepository"),
        arguments(
            "adapterusesapplicationservice",
            Set.of(ADAPTERS_MUST_USE_THE_APPLICATION_THROUGH_PORTS),
            "application.PlaceOrder"),
        arguments(
            "inboundusesoutputport",
            Set.of(INBOUND_ADAPTERS_USE_ONLY_INPUT_PORTS),
            "application.port.out.OrderRepository"),
        arguments(
            "outboundusesinputport",
            Set.of(OUTBOUND_ADAPTERS_USE_ONLY_OUTPUT_PORTS),
            "application.port.in.PlaceOrder"),
        arguments("cycle", Set.of(PACKAGES_MUST_BE_FREE_OF_CYCLES), "domain.fill.Fill"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void fixtureViolatesExactlyTheExpectedRules(
      String fixture, Set<ArchRule> expected, String forbiddenDependency) {
    JavaClasses classes = importFixture(fixture);

    Set<ArchRule> violated =
        HexagonalArchitecture.rules().stream()
            .filter(rule -> rule.evaluate(classes).hasViolation())
            .collect(toSet());

    assertEquals(descriptions(expected), descriptions(violated));
    for (ArchRule rule : violated) {
      String report = rule.evaluate(classes).getFailureReport().toString();
      assertTrue(report.contains(forbiddenDependency), report);
    }
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

    String message = requireNonNull(failure.getMessage());
    assertTrue(message.contains("domainusesspring.domain.Order"));
    assertTrue(message.contains("org.springframework.util.Assert"));
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
