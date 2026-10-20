package com.argus.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;

/**
 * Dependency rules of the hexagonal architecture shared by every Argus service.
 *
 * <p>A service applies all of them with a single test class:
 *
 * <pre>{@code
 * @AnalyzeClasses(packages = "com.argus.feedgateway", importOptions = DoNotIncludeTests.class)
 * class ArchitectureTest {
 *   @ArchTest static final ArchTests hexagonal = ArchTests.in(HexagonalArchitecture.class);
 * }
 * }</pre>
 *
 * <p>Layers are recognised by package name anywhere below the service root: {@code domain}, {@code
 * application} (with its ports in {@code application.port.in} and {@code application.port.out}),
 * {@code adapter.in} and {@code adapter.out}. See ADR 0009 for the layout. {@code config} belongs
 * to no layer and is the only code outside them allowed to use them, so that it can wire everything
 * together. A layer that a service does not have yet is not an error.
 */
public final class HexagonalArchitecture {

  static final String DOMAIN = "..domain..";
  static final String APPLICATION = "..application..";
  static final String PORT_IN = "..application.port.in..";
  static final String PORT_OUT = "..application.port.out..";
  static final String ADAPTER_IN = "..adapter.in..";
  static final String ADAPTER_OUT = "..adapter.out..";
  static final String CONFIG = "..config..";

  static final String SPRING = "org.springframework..";
  static final String KAFKA = "org.apache.kafka..";
  static final String AERON = "io.aeron..";

  /**
   * ArchUnit's onion architecture, which is the hexagonal one: the domain depends on no other
   * layer, the application only on the domain, and adapters never on each other.
   */
  @ArchTest
  public static final ArchRule LAYERS_MUST_RESPECT_THE_HEXAGON =
      onionArchitecture()
          .domainModels(DOMAIN)
          .applicationServices(APPLICATION)
          .adapter("inbound", ADAPTER_IN)
          .adapter("outbound", ADAPTER_OUT)
          .withOptionalLayers(true)
          .ignoreDependency(resideInAPackage(CONFIG), alwaysTrue())
          .because("dependencies point inwards: adapters are plugged into ports by config");

  /** Not covered by the onion architecture, which only checks dependencies between layers. */
  @ArchTest
  public static final ArchRule DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS =
      noClasses()
          .that()
          .resideInAPackage(DOMAIN)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(SPRING, KAFKA, AERON)
          .as("domain must not depend on Spring, Kafka or Aeron")
          .because("the domain is pure Java, and it is what keeps the hot path framework-free")
          .allowEmptyShould(true);

  /** Not covered by the onion architecture either: config belongs to no layer. */
  @ArchTest
  public static final ArchRule INNER_LAYERS_MUST_NOT_DEPEND_ON_CONFIG =
      noClasses()
          .that()
          .resideInAnyPackage(DOMAIN, APPLICATION)
          .should()
          .dependOnClassesThat()
          .resideInAPackage(CONFIG)
          .as("domain and application must not depend on config")
          .because("config is Spring wiring, and it would bring the framework into the hexagon")
          .allowEmptyShould(true);

  /** Ports have a fixed home, so that the boundary of a service is visible in its packages. */
  @ArchTest
  public static final ArchRule PORTS_MUST_LIVE_IN_PORT_PACKAGES =
      classes()
          .that()
          .areInterfaces()
          .and()
          .resideInAPackage(APPLICATION)
          // package-info compiles to a synthetic interface
          .and()
          .doNotHaveSimpleName("package-info")
          .should()
          .resideInAnyPackage(PORT_IN, PORT_OUT)
          .as("interfaces in application must be ports, in application.port.in or port.out")
          .because(
              "a port's package says who calls whom: port.in into the application, port.out out")
          .allowEmptyShould(true);

  /** The onion rule lets adapters use any application class; only ports are meant for them. */
  @ArchTest
  public static final ArchRule ADAPTERS_MUST_USE_THE_APPLICATION_THROUGH_PORTS =
      noClasses()
          .that()
          .resideInAnyPackage(ADAPTER_IN, ADAPTER_OUT)
          .should()
          .dependOnClassesThat(
              resideInAPackage(APPLICATION).and(not(resideInAnyPackage(PORT_IN, PORT_OUT))))
          .as("adapters must use the application only through its ports")
          .because("a service behind a port can change without touching the adapters")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule PACKAGES_MUST_BE_FREE_OF_CYCLES =
      slices()
          .matching("com.argus.(**)")
          .should()
          .beFreeOfCycles()
          .as("packages must be free of dependency cycles")
          .allowEmptyShould(true);

  /** Every rule above, in declaration order. */
  public static List<ArchRule> rules() {
    return List.of(
        LAYERS_MUST_RESPECT_THE_HEXAGON,
        DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS,
        INNER_LAYERS_MUST_NOT_DEPEND_ON_CONFIG,
        PORTS_MUST_LIVE_IN_PORT_PACKAGES,
        ADAPTERS_MUST_USE_THE_APPLICATION_THROUGH_PORTS,
        PACKAGES_MUST_BE_FREE_OF_CYCLES);
  }

  private HexagonalArchitecture() {}
}
