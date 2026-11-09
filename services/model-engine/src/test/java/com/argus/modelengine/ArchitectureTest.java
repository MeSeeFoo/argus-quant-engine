package com.argus.modelengine;

import com.argus.architecture.HexagonalArchitecture;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

@AnalyzeClasses(packages = "com.argus.modelengine", importOptions = DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest static final ArchTests hexagonal = ArchTests.in(HexagonalArchitecture.class);
}
