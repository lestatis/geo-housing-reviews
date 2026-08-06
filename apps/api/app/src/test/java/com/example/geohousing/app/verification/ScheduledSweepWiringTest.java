package com.example.geohousing.app.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * That the sweeps are actually invoked in a running application.
 *
 * <p>Badge expiry was written, tested, and never called: no scheduled adapter existed, so a lapsed
 * badge stayed approved forever in production while every unit test passed. A service that works
 * and is never run is indistinguishable from one that does not work, and only the wiring can tell
 * them apart — so the wiring is what this asserts.
 *
 * <p>Each job is checked for a real trigger rather than merely for existing as a bean: a
 * {@code @Scheduled} method whose annotation was dropped would still be a perfectly healthy bean.
 */
@Testcontainers
@SpringBootTest
class ScheduledSweepWiringTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Autowired private ApplicationContext context;

  @Test
  void lapsedVerificationBadgesAreSweptOnASchedule() {
    assertThat(scheduledMethodsOf("verificationExpiryJob"))
        .as("a badge that expires only when a test calls it does not expire in production")
        .isNotEmpty();
  }

  @Test
  void lapsedEvidenceIsSweptOnASchedule() {
    // The sweep that deletes raw identity and tenancy documents. Nothing else deletes them.
    assertThat(scheduledMethodsOf("evidenceRetentionJob")).isNotEmpty();
  }

  @Test
  void bothSweepsAreBoundedSoABacklogCannotMonopolizeAWorker() {
    var expiry =
        context.getBean(
            com.example.geohousing.verification.infrastructure.VerificationExpiryProperties.class);

    assertThat(expiry.sweepBatchSize()).isPositive();
    assertThat(expiry.sweepFixedDelay()).isGreaterThan(Duration.ZERO);
  }

  private java.util.List<Method> scheduledMethodsOf(String beanName) {
    Object job = context.getBean(beanName);
    return java.util.Arrays.stream(
            org.springframework.util.ClassUtils.getUserClass(job).getMethods())
        .filter(method -> method.isAnnotationPresent(Scheduled.class))
        .toList();
  }
}
