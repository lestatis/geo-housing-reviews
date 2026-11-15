package com.example.geohousing.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.app.support.SharedPostgis;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.Extensions;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Keeps the shared-database wiring in the order the reset depends on.
 *
 * <p>JUnit registers class-level extensions in declaration order. A test that wires the shared
 * database must declare the reset extension before {@code @SpringBootTest}, so its {@code
 * beforeAll} truncates before Spring creates or reuses a context. The fixtures below prove that the
 * guard fails on both a missing extension and a reset declared after Spring.
 */
@AnalyzeClasses(
    packages = "com.example.geohousing.app",
    importOptions = ImportOption.OnlyIncludeTests.class)
class SharedPostgisWiringArchitectureTest {

  private static final DescribedPredicate<JavaClass> CALLS_SHARED_POSTGIS_REGISTER =
      new DescribedPredicate<>("call SharedPostgis.register") {
        @Override
        public boolean test(JavaClass javaClass) {
          return callsSharedPostgisRegister(javaClass);
        }
      };

  private static final ArchRule SHARED_POSTGIS_WIRING =
      classes()
          .that(CALLS_SHARED_POSTGIS_REGISTER)
          .and()
          .doNotHaveSimpleName("SharedPostgisResetTest")
          .and()
          // The harness delegates register(registry) to register(registry, poolSize), which
          // ArchUnit sees as a call from SharedPostgis to itself. It is not a test class.
          .doNotHaveSimpleName("SharedPostgis")
          .and()
          .haveSimpleNameNotEndingWith("Fixture")
          .should(new DeclaresResetExtensionBeforeSpringBoot())
          .allowEmptyShould(true);

  @ArchTest static final ArchRule shared_postgis_wiring = SHARED_POSTGIS_WIRING;

  // The real rule excludes *Fixture classes so they cannot fail the normal architecture scan.
  // The fixture checks below need the same condition without that exclusion.
  private static final ArchRule FIXTURE_WIRING =
      classes()
          .that(CALLS_SHARED_POSTGIS_REGISTER)
          .should(new DeclaresResetExtensionBeforeSpringBoot())
          .allowEmptyShould(true);

  @Test
  void guardRejectsRegistrationWithoutTheResetExtension() {
    assertGuardRejects(MissingResetExtensionFixture.class);
  }

  @Test
  void guardRejectsTheResetExtensionDeclaredAfterSpringBootTest() {
    assertGuardRejects(MisorderedWiringFixture.class);
  }

  private static void assertGuardRejects(Class<?> fixture) {
    assertThatThrownBy(() -> FIXTURE_WIRING.check(new ClassFileImporter().importClasses(fixture)))
        .as("the wiring guard must reject %s", fixture.getSimpleName())
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(fixture.getName());
  }

  private static boolean callsSharedPostgisRegister(JavaClass javaClass) {
    return javaClass.getMethodCallsFromSelf().stream()
        .anyMatch(
            call ->
                call.getTarget().getOwner().isEquivalentTo(SharedPostgis.class)
                    && call.getTarget().getName().equals("register"));
  }

  private static String wiringViolation(JavaClass javaClass) {
    Annotation[] annotations = declaredAnnotations(javaClass);
    int resetIndex = -1;
    int springBootTestIndex = -1;
    for (int index = 0; index < annotations.length; index++) {
      if (annotations[index].annotationType().equals(SpringBootTest.class)) {
        springBootTestIndex = index;
      }
      if (declaresResetExtension(annotations[index])) {
        resetIndex = index;
      }
    }
    if (resetIndex < 0) {
      return javaClass.getName()
          + " calls SharedPostgis.register but does not declare"
          + " @ExtendWith(SharedPostgis.ResetBeforeClass.class)";
    }
    if (springBootTestIndex >= 0 && resetIndex > springBootTestIndex) {
      return javaClass.getName()
          + " declares @ExtendWith(SharedPostgis.ResetBeforeClass.class) after @SpringBootTest,"
          + " so Spring registers its extension first";
    }
    return null;
  }

  private static boolean declaresResetExtension(Annotation annotation) {
    if (annotation instanceof ExtendWith extendWith) {
      return Arrays.stream(extendWith.value())
          .anyMatch(extension -> extension == SharedPostgis.ResetBeforeClass.class);
    }
    if (annotation instanceof Extensions extensions) {
      return Arrays.stream(extensions.value())
          .anyMatch(SharedPostgisWiringArchitectureTest::declaresResetExtension);
    }
    return false;
  }

  private static Annotation[] declaredAnnotations(JavaClass javaClass) {
    try {
      Class<?> reflected =
          Class.forName(
              javaClass.getName(),
              false,
              SharedPostgisWiringArchitectureTest.class.getClassLoader());
      return reflected.getDeclaredAnnotations();
    } catch (ClassNotFoundException missing) {
      throw new IllegalStateException(
          "could not inspect the declared annotations of " + javaClass.getName(), missing);
    }
  }

  private static final class DeclaresResetExtensionBeforeSpringBoot
      extends ArchCondition<JavaClass> {

    private DeclaresResetExtensionBeforeSpringBoot() {
      super("declare @ExtendWith(SharedPostgis.ResetBeforeClass.class) before @SpringBootTest");
    }

    @Override
    public void check(JavaClass javaClass, ConditionEvents events) {
      String violation = wiringViolation(javaClass);
      if (violation != null) {
        events.add(SimpleConditionEvent.violated(javaClass, violation));
      }
    }
  }

  @SpringBootTest
  static class MissingResetExtensionFixture {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
      SharedPostgis.register(registry);
    }
  }

  @SpringBootTest
  @ExtendWith(SharedPostgis.ResetBeforeClass.class)
  static class MisorderedWiringFixture {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
      SharedPostgis.register(registry);
    }
  }
}
