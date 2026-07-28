package com.example.geohousing.app.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Proves the moderation schema enforces the parts of MODERATION.md that must not depend on
 * application code remembering them: cases converge, a reporter cannot report-bomb, an adverse
 * decision owes the user an explanation, and an appeal is decided once, by someone else.
 */
@Testcontainers
@SpringBootTest
class ModerationMigrationIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void moderationMigrationApplied() {
    Integer applied =
        jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where version = '6.1' and success = true",
            Integer.class);
    assertThat(applied).isEqualTo(1);
  }

  @Test
  void manyReportsAboutOneTargetConvergeOnASingleLiveCase() {
    UUID target = UUID.randomUUID();
    openCase(target, "REPORT");

    // Otherwise a coordinated group could open a case per account and bury the queue — the
    // brigading MODERATION.md asks the platform to resist.
    assertThatThrownBy(() -> openCase(target, "LEGAL_REQUEST"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aSettledCaseDoesNotBlockAFreshOneIfTheContentIsReportedAgain() {
    UUID target = UUID.randomUUID();
    UUID first = openCase(target, "REPORT");
    jdbcTemplate.update(
        "update moderation.moderation_case set status = 'CLOSED', closed_at = now() where id = ?",
        first);

    assertThatCode(() -> openCase(target, "REPORT")).doesNotThrowAnyException();
  }

  @Test
  void aCaseBeingWorkedHasSomeoneAccountableForIt() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into moderation.moderation_case"
                        + " (target_type, target_id, trigger_source, status)"
                        + " values ('REVIEW', ?, 'REPORT', 'IN_REVIEW')",
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void oneAccountCannotReportTheSameContentRepeatedly() {
    UUID target = UUID.randomUUID();
    UUID reporter = UUID.randomUUID();
    report(target, reporter, "PERSONAL_DATA", null);

    assertThatThrownBy(() -> report(target, reporter, "HARASSMENT_OR_THREAT", null))
        .isInstanceOf(DataIntegrityViolationException.class);

    // A different account raising the same concern is exactly what the queue needs to see.
    assertThatCode(() -> report(target, UUID.randomUUID(), "PERSONAL_DATA", null))
        .doesNotThrowAnyException();
  }

  @Test
  void aClosedOutReportLetsTheSameAccountRaiseSomethingNewLater() {
    UUID target = UUID.randomUUID();
    UUID reporter = UUID.randomUUID();
    UUID caseId = openCase(target, "REPORT");
    report(target, reporter, "PERSONAL_DATA", null);
    jdbcTemplate.update(
        "update moderation.report set status = 'DISMISSED', case_id = ?"
            + " where reporter_account_id = ?",
        caseId,
        reporter);

    assertThatCode(() -> report(target, reporter, "OUTDATED_OR_RESOLVED", null))
        .doesNotThrowAnyException();
  }

  @Test
  void anOtherReportMustSayWhatIsWrong() {
    UUID target = UUID.randomUUID();

    // "Other" with nothing written gives a moderator nothing to act on.
    assertThatThrownBy(() -> report(target, UUID.randomUUID(), "OTHER", null))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> report(target, UUID.randomUUID(), "OTHER", "   "))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatCode(() -> report(target, UUID.randomUUID(), "OTHER", "Posted our building code."))
        .doesNotThrowAnyException();
  }

  @Test
  void aReportPastIntakeIsAttachedToTheCaseItIsEvidenceFor() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into moderation.report"
                        + " (target_type, target_id, reporter_account_id, category, status)"
                        + " values ('REVIEW', ?, ?, 'DUPLICATE_OR_SPAM', 'LINKED')",
                    UUID.randomUUID(),
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aDecisionThatCostsTheUserSomethingMustExplainWhy() {
    UUID caseId = openCase(UUID.randomUUID(), "REPORT");

    assertThatThrownBy(() -> decide(caseId, "REMOVE", "DOXXING", null, UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> decide(caseId, "HIDE", "DOXXING", "  ", UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);

    // Approving takes nothing away, so it owes the author no explanation.
    assertThatCode(() -> decide(caseId, "APPROVE", "CLEAN", null, UUID.randomUUID()))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                decide(
                    caseId,
                    "REMOVE",
                    "DOXXING",
                    "Your review identified a neighbour.",
                    UUID.randomUUID()))
        .doesNotThrowAnyException();
  }

  @Test
  void aDecisionAlwaysCarriesAReasonCode() {
    UUID caseId = openCase(UUID.randomUUID(), "REPORT");

    assertThatThrownBy(() -> decide(caseId, "APPROVE", "   ", null, UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void anAppealIsHeardOnceAndDecidedBySomeoneOtherThanTheOriginalModerator() {
    UUID caseId = openCase(UUID.randomUUID(), "REPORT");
    UUID moderator = UUID.randomUUID();
    UUID decisionId =
        decide(caseId, "REMOVE", "DOXXING", "Your review identified a neighbour.", moderator);
    UUID appellant = UUID.randomUUID();
    appeal(decisionId, appellant, moderator);

    // "Permit one structured appeal" — a second bite is not an appeal, it is attrition.
    assertThatThrownBy(() -> appeal(decisionId, UUID.randomUUID(), moderator))
        .isInstanceOf(DataIntegrityViolationException.class);

    // Due process is enforced here rather than only in a service, because a service can be
    // bypassed by the next code path that forgets the rule.
    assertThatThrownBy(() -> resolveAppeal(decisionId, moderator))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatCode(() -> resolveAppeal(decisionId, UUID.randomUUID())).doesNotThrowAnyException();
  }

  @Test
  void aDecidedAppealOwesTheAppellantAnOutcomeAndATimestamp() {
    UUID caseId = openCase(UUID.randomUUID(), "REPORT");
    UUID moderator = UUID.randomUUID();
    UUID decisionId =
        decide(caseId, "REJECT", "SPAM", "Your review repeated an existing one.", moderator);
    appeal(decisionId, UUID.randomUUID(), moderator);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "update moderation.appeal set status = 'UPHELD',"
                        + " decided_by_account_id = ?, outcome_explanation = 'Reviewed again.'"
                        + " where decision_id = ?",
                    UUID.randomUUID(),
                    decisionId))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "update moderation.appeal set status = 'UPHELD',"
                        + " decided_by_account_id = ?, decided_at = now() where decision_id = ?",
                    UUID.randomUUID(),
                    decisionId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void moderationOwnsNoForeignKeyIntoAnotherModulesTables() {
    // Reports and cases point at reviews by opaque id. A real foreign key here would couple the
    // schemas and let one module's migration break another's.
    Integer crossModuleKeys =
        jdbcTemplate.queryForObject(
            "select count(*) from pg_constraint c"
                + " join pg_class t on t.oid = c.conrelid"
                + " join pg_namespace n on n.oid = t.relnamespace"
                + " join pg_class f on f.oid = c.confrelid"
                + " join pg_namespace fn on fn.oid = f.relnamespace"
                + " where c.contype = 'f' and n.nspname = 'moderation'"
                + " and fn.nspname <> 'moderation'",
            Integer.class);
    assertThat(crossModuleKeys).isZero();
  }

  private UUID openCase(UUID target, String trigger) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into moderation.moderation_case (id, target_type, target_id, trigger_source)"
            + " values (?, 'REVIEW', ?, ?)",
        id,
        target,
        trigger);
    return id;
  }

  private void report(UUID target, UUID reporter, String category, String description) {
    jdbcTemplate.update(
        "insert into moderation.report"
            + " (target_type, target_id, reporter_account_id, category, description)"
            + " values ('REVIEW', ?, ?, ?, ?)",
        target,
        reporter,
        category,
        description);
  }

  private UUID decide(
      UUID caseId, String action, String reasonCode, String publicExplanation, UUID decidedBy) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into moderation.moderation_decision"
            + " (id, case_id, action, reason_code, public_explanation, decided_by_account_id)"
            + " values (?, ?, ?, ?, ?, ?)",
        id,
        caseId,
        action,
        reasonCode,
        publicExplanation,
        decidedBy);
    return id;
  }

  private void appeal(UUID decisionId, UUID appellant, UUID originalDecider) {
    jdbcTemplate.update(
        "insert into moderation.appeal"
            + " (decision_id, appellant_account_id, appeal_text, original_decider_account_id)"
            + " values (?, ?, 'The flat number was my own.', ?)",
        decisionId,
        appellant,
        originalDecider);
  }

  private void resolveAppeal(UUID decisionId, UUID decidedBy) {
    jdbcTemplate.update(
        "update moderation.appeal set status = 'OVERTURNED', decided_by_account_id = ?,"
            + " outcome_explanation = 'Restored: the flat was the author''s own.',"
            + " decided_at = now() where decision_id = ?",
        decidedBy,
        decisionId);
  }
}
