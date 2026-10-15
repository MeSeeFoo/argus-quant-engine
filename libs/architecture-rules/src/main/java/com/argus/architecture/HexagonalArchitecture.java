package com.argus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
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
 * application}, {@code adapter.in}, {@code adapter.out} and {@code config}. A layer that a service
 * does not have yet is not an error.
 */
public final class HexagonalArchitecture {

  static final String DOMAIN = "..domain..";
  static final String APPLICATION = "..application..";
  static final String ADAPTER = "..adapter..";
  static final String ADAPTER_IN = "..adapter.in..";
  static final String ADAPTER_OUT = "..adapter.out..";
  static final String CONFIG = "..config..";

  static final String SPRING = "org.springframework..";
  static final String KAFKA = "org.apache.kafka..";
  static final String AERON = "io.aeron..";

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

  @ArchTest
  public static final ArchRule DOMAIN_MUST_NOT_DEPEND_ON_OTHER_LAYERS =
      noClasses()
          .that()
          .resideInAPackage(DOMAIN)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(APPLICATION, ADAPTER, CONFIG)
          .as("domain must not depend on application, adapters or config")
          .because("the domain is the centre of the hexagon: every other layer depends on it")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule APPLICATION_MUST_NOT_DEPEND_ON_ADAPTERS =
      noClasses()
          .that()
          .resideInAPackage(APPLICATION)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(ADAPTER, CONFIG)
          .as("application must not depend on adapters or config")
          .because("use cases talk to the outside world only through their ports")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule ONLY_CONFIG_MAY_DEPEND_ON_ADAPTERS =
      noClasses()
          .that()
          .resideOutsideOfPackages(ADAPTER, CONFIG)
          .should()
          .dependOnClassesThat()
          .resideInAPackage(ADAPTER)
          .as("only config may depend on adapters")
          .because("adapters are plugged into ports by the wiring, never referenced directly")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule INBOUND_ADAPTERS_MUST_NOT_DEPEND_ON_OUTBOUND_ADAPTERS =
      noClasses()
          .that()
          .resideInAPackage(ADAPTER_IN)
          .should()
          .dependOnClassesThat()
          .resideInAPackage(ADAPTER_OUT)
          .as("inbound adapters must not depend on outbound adapters")
          .because(
              "an inbound adapter drives a use case, which reaches outbound adapters via ports")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule OUTBOUND_ADAPTERS_MUST_NOT_DEPEND_ON_INBOUND_ADAPTERS =
      noClasses()
          .that()
          .resideInAPackage(ADAPTER_OUT)
          .should()
          .dependOnClassesThat()
          .resideInAPackage(ADAPTER_IN)
          .as("outbound adapters must not depend on inbound adapters")
          .because("an outbound adapter implements a port and knows nothing about who calls it")
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
        DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS,
        DOMAIN_MUST_NOT_DEPEND_ON_OTHER_LAYERS,
        APPLICATION_MUST_NOT_DEPEND_ON_ADAPTERS,
        ONLY_CONFIG_MAY_DEPEND_ON_ADAPTERS,
        INBOUND_ADAPTERS_MUST_NOT_DEPEND_ON_OUTBOUND_ADAPTERS,
        OUTBOUND_ADAPTERS_MUST_NOT_DEPEND_ON_INBOUND_ADAPTERS,
        PACKAGES_MUST_BE_FREE_OF_CYCLES);
  }

  private HexagonalArchitecture() {}
}
