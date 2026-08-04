package com.example.geohousing.app.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.geohousing.moderation.api.ModerationMetrics;
import com.example.geohousing.reviews.api.ReviewMetrics;
import com.example.geohousing.verification.api.VerificationMetrics;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The counting itself, against a real database.
 *
 * <p>The acceptance scenarios prove the numbers mean what they say end to end. What they cannot
 * place is a row at an exact instant, and the window boundary is where a count goes quietly wrong:
 * every query here is half-open, {@code from} inclusive and {@code until} exclusive, matching the
 * audit timeline so the two can never disagree about which day something fell on.
 *
 * <p>Rows are written with {@code JdbcTemplate} because the point is the timestamp, which no
 * sequence of HTTP calls can be made to choose.
 */
@Testcontainers
@SpringBootTest
class AdminMetricsIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Autowired private ModerationMetrics moderation;
  @Autowired private ReviewMetrics reviews;
  @Autowired private VerificationMetrics verification;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void aDecisionAtTheWindowsExclusiveEndIsOutsideIt() {
    Instant start = Instant.parse("2026-05-01T00:00:00Z");
    Instant end = Instant.parse("2026-05-02T00:00:00Z");
    UUID caseId = openCase(start.minusSeconds(60), "CLOSED");

    recordDecision(caseId, start); // the inclusive start: inside
    recordDecision(caseId, end.minusMillis(1)); // the last instant of the window: inside
    recordDecision(caseId, end); // the exclusive end: outside

    assertThat(moderation.between(start, end).decisions()).isEqualTo(2);
  }

  @Test
  void anAppealIsHeardOnlyOnceItHasAnOutcome() {
    Instant start = Instant.parse("2026-05-10T00:00:00Z");
    Instant end = start.plus(1, ChronoUnit.DAYS);
    UUID caseId = openCase(start.minusSeconds(60), "CLOSED");

    appeal(recordDecision(caseId, start), "PENDING", null);
    appeal(recordDecision(caseId, start), "UPHELD", start.plusSeconds(10));
    appeal(recordDecision(caseId, start), "OVERTURNED", start.plusSeconds(20));

    assertThat(moderation.between(start, end).appealsHeard())
        .as("a pending appeal is work outstanding, not work done")
        .isEqualTo(2);
    assertThat(moderation.between(start, end).appealsOverturned()).isEqualTo(1);
  }

  @Test
  void aClosedCaseIsNotAnOpenOne() {
    long before = moderation.openCases();
    openCase(Instant.parse("2026-05-20T00:00:00Z"), "CLOSED");

    assertThat(moderation.openCases()).isEqualTo(before);
  }

  @Test
  void everyNonTerminalStatusCountsAsOpen() {
    // "Decided" and "appealed" are still the queue's problem: something is expected to happen next.
    long before = moderation.openCases();
    for (String status : new String[] {"OPEN", "IN_REVIEW", "DECIDED", "APPEALED"}) {
      openCase(Instant.parse("2026-05-21T00:00:00Z"), status);
    }

    assertThat(moderation.openCases()).isEqualTo(before + 4);
  }

  @Test
  void theOldestOpenCaseIsTheOneThatHasWaitedLongest() {
    Instant ancient = Instant.parse("2020-01-01T00:00:00Z");
    openCase(ancient, "OPEN");
    openCase(Instant.parse("2026-06-01T00:00:00Z"), "OPEN");

    assertThat(moderation.oldestOpenCaseAt()).contains(ancient);
  }

  @Test
  void countingQueuesAndThroughputWorksOnAnEmptyWindow() {
    // Far future: nothing has happened there, and every count must say zero rather than fail.
    Instant start = Instant.parse("2030-01-01T00:00:00Z");
    Instant end = start.plus(1, ChronoUnit.DAYS);

    assertThat(moderation.between(start, end).decisions()).isZero();
    assertThat(reviews.between(start, end).published()).isZero();
    assertThat(verification.between(start, end).approved()).isZero();
    assertThat(reviews.awaitingModeration()).isNotNegative();
    assertThat(verification.pendingCases()).isNotNegative();
  }

  /**
   * The schema is stricter than a naive fixture: an {@code IN_REVIEW} case must have an assignee
   * and a {@code CLOSED} one must have a closing time. Honouring both here keeps the rows realistic
   * rather than working around a constraint that exists for a reason.
   */
  private UUID openCase(Instant openedAt, String status) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into moderation.moderation_case"
            + " (id, target_type, target_id, trigger_source, status, risk_level,"
            + " assigned_moderator_account_id, opened_at, closed_at)"
            + " values (?, 'REVIEW', ?, 'REPORT', ?, 'STANDARD', ?, ?, ?)",
        id,
        UUID.randomUUID(),
        status,
        "IN_REVIEW".equals(status) ? UUID.randomUUID() : null,
        java.sql.Timestamp.from(openedAt),
        "CLOSED".equals(status) ? java.sql.Timestamp.from(openedAt.plusSeconds(60)) : null);
    return id;
  }

  /**
   * A removal carries a public explanation because the database insists: "no takedown without
   * telling the author why" is a CHECK, not a convention. Writing the row honestly rather than
   * around it keeps the fixture describing something the product could actually produce.
   */
  private UUID recordDecision(UUID caseId, Instant decidedAt) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into moderation.moderation_decision"
            + " (id, case_id, decided_by_account_id, action, reason_code, policy_version,"
            + " public_explanation, decided_at)"
            + " values (?, ?, ?, 'REMOVE', 'DOXXING', 1, 'Your review named a neighbour.', ?)",
        id,
        caseId,
        UUID.randomUUID(),
        java.sql.Timestamp.from(decidedAt));
    return id;
  }

  /**
   * A decided appeal carries an outcome explanation, which the database also insists on: an appeal
   * answered without a reason is not an answer. Only a pending one may leave it empty.
   */
  private void appeal(UUID decisionId, String status, Instant decidedAt) {
    jdbcTemplate.update(
        "insert into moderation.appeal"
            + " (id, decision_id, appellant_account_id, appeal_text, status, outcome_explanation,"
            + " original_decider_account_id, decided_by_account_id, created_at, decided_at)"
            + " values (?, ?, ?, 'Please reconsider.', ?, ?, ?, ?, now(), ?)",
        UUID.randomUUID(),
        decisionId,
        UUID.randomUUID(),
        status,
        decidedAt == null ? null : "Considered again.",
        UUID.randomUUID(),
        decidedAt == null ? null : UUID.randomUUID(),
        decidedAt == null ? null : java.sql.Timestamp.from(decidedAt));
  }
}
