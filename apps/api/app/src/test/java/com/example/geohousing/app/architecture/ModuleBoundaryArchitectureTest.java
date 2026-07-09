package com.example.geohousing.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@AnalyzeClasses(
    packages = "com.example.geohousing",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryArchitectureTest {

  private static final Pattern MODULE_PACKAGE =
      Pattern.compile("com\\.example\\.geohousing\\.([a-z]+)(\\..*)?");

  private static String moduleOf(JavaClass javaClass) {
    Matcher matcher = MODULE_PACKAGE.matcher(javaClass.getPackageName());
    return matcher.matches() ? matcher.group(1) : null;
  }

  @ArchTest
  static final ArchRule modules_should_be_free_of_cycles =
      SlicesRuleDefinition.slices()
          .matching("com.example.geohousing.(*)..")
          .should()
          .beFreeOfCycles();

  // Gradle module isolation already blocks this for as long as no sibling module
  // declares a dependency on another sibling. This rule is the fallback once such
  // a dependency is legitimately added (e.g. reviews -> properties) so that only
  // the target module's api package, not its domain/application/infrastructure
  // internals, is reachable from outside the module.
  @ArchTest
  static final ArchRule modules_should_only_be_reached_through_their_api_package =
      classes()
          .should(
              new ArchCondition<JavaClass>("only depend on other modules' api packages") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                  String originModule = moduleOf(javaClass);
                  javaClass
                      .getDirectDependenciesFromSelf()
                      .forEach(
                          dependency -> {
                            JavaClass target = dependency.getTargetClass();
                            String targetModule = moduleOf(target);
                            boolean crossModule =
                                originModule != null
                                    && targetModule != null
                                    && !originModule.equals(targetModule)
                                    && !"shared".equals(targetModule);
                            boolean reachesInternals =
                                crossModule
                                    && !target
                                        .getPackageName()
                                        .contains("." + targetModule + ".api");
                            if (reachesInternals) {
                              events.add(
                                  SimpleConditionEvent.violated(
                                      javaClass,
                                      javaClass.getName()
                                          + " depends on "
                                          + target.getName()
                                          + " outside module '"
                                          + targetModule
                                          + "'s api package"));
                            }
                          });
                }
              })
          .allowEmptyShould(true);

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
