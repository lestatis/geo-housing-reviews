package com.example.geohousing.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

@AnalyzeClasses(
    packages = "com.example.geohousing",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryArchitectureTest {

  @ArchTest
  static final ArchRule modules_should_be_free_of_cycles =
      SlicesRuleDefinition.slices()
          .matching("com.example.geohousing.(*)..")
          .should()
          .beFreeOfCycles();

  // allowEmptyShould: no domain classes exist yet, only package-info.java stubs.
  // These rules activate as soon as real domain code lands in a later task.
  @ArchTest
  static final ArchRule domain_packages_should_not_depend_on_spring =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.springframework..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule domain_packages_should_not_depend_on_infrastructure_packages =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..infrastructure..")
          .allowEmptyShould(true);
}
