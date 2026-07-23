package com.example.geohousing.app.verification;

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

@Testcontainers
@SpringBootTest
class VerificationMigrationIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void verificationMigrationApplied() {
    Integer applied =
        jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where version = '5.1' and success = true",
            Integer.class);
    assertThat(applied).isEqualTo(1);
  }

  @Test
  void verificationTablesExist() {
    for (String table : new String[] {"verification_case", "verification_decision_audit_event"}) {
      Integer count =
          jdbcTemplate.queryForObject(
              "select count(*) from information_schema.tables"
                  + " where table_schema = 'verification' and table_name = ?",
              Integer.class,
              table);
      assertThat(count).as("table verification.%s exists", table).isEqualTo(1);
    }
  }

  @Test
  void claimMethodStatusAndTierRejectUnknownValues() {
    assertThatThrownBy(() -> insertCase("TENANT", "INVITATION", "PENDING", "UNVERIFIED"))
        .isInstanceOf(DataIntegrityViolationException.class);
    // DOCUMENT (Tier 2) is intentionally not yet an accepted method; it arrives with the evidence
    // subsystem in a later migration.
    assertThatThrownBy(() -> insertCase("OWNER", "DOCUMENT", "PENDING", "UNVERIFIED"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertCase("OWNER", "INVITATION", "ARCHIVED", "UNVERIFIED"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertCase("OWNER", "INVITATION", "PENDING", "GOLD"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void anApprovedCaseMustRecordWhenItWasVerifiedAndWhoDecidedIt() {
    // Approved without verified_at.
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into verification.verification_case"
                        + " (account_id, property_id, relationship_claim, method, status,"
                        + "  decided_by, decision_reason_code)"
                        + " values (?, ?, 'OWNER', 'INVITATION', 'APPROVED', ?, 'CLEAN')",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
    // Approved without a decider/reason.
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into verification.verification_case"
                        + " (account_id, property_id, relationship_claim, method, status,"
                        + "  verified_at)"
                        + " values (?, ?, 'OWNER', 'INVITATION', 'APPROVED', now())",
                    UUID.randomUUID(),
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void atMostOneLiveCasePerAccountAndProperty() {
    UUID account = UUID.randomUUID();
    UUID property = UUID.randomUUID();
    insertCase(account, property, "CURRENT_RESIDENT", "BUILDING_CODE", "PENDING");

    assertThatThrownBy(() -> insertCase(account, property, "OWNER", "INVITATION", "PENDING"))
        .isInstanceOf(DataIntegrityViolationException.class);

    // A rejected case (properly decided) frees the slot for a fresh attempt.
    jdbcTemplate.update(
        "update verification.verification_case set status = 'REJECTED', decided_by = ?,"
            + " decision_reason_code = 'FORGED_INVITE' where account_id = ? and property_id = ?",
        UUID.randomUUID(),
        account,
        property);
    assertThatCode(() -> insertCase(account, property, "OWNER", "INVITATION", "PENDING"))
        .doesNotThrowAnyException();
  }

  @Test
  void aDecisionAuditRowRequiresANonBlankReasonCode() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into verification.verification_decision_audit_event"
                        + " (id, actor_account_id, action, case_id, reason_code, outcome)"
                        + " values (?, ?, 'APPROVE', ?, '   ', 'APPLIED')",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void onlySystemExpiryMayOmitTheActor() {
    // A human decision without an actor is refused.
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into verification.verification_decision_audit_event"
                        + " (id, action, case_id, reason_code, outcome)"
                        + " values (?, 'APPROVE', ?, 'CLEAN', 'APPLIED')",
                    UUID.randomUUID(),
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
    // System-initiated expiry may omit it.
    assertThatCode(
            () ->
                jdbcTemplate.update(
                    "insert into verification.verification_decision_audit_event"
                        + " (id, action, case_id, reason_code, outcome)"
                        + " values (?, 'EXPIRE', ?, 'POLICY_EXPIRY', 'APPLIED')",
                    UUID.randomUUID(),
                    UUID.randomUUID()))
        .doesNotThrowAnyException();
  }

  private void insertCase(String claim, String method, String status, String tier) {
    jdbcTemplate.update(
        "insert into verification.verification_case"
            + " (account_id, property_id, relationship_claim, method, status, tier)"
            + " values (?, ?, ?, ?, ?, ?)",
        UUID.randomUUID(),
        UUID.randomUUID(),
        claim,
        method,
        status,
        tier);
  }

  private void insertCase(UUID account, UUID property, String claim, String method, String status) {
    jdbcTemplate.update(
        "insert into verification.verification_case"
            + " (account_id, property_id, relationship_claim, method, status)"
            + " values (?, ?, ?, ?, ?)",
        account,
        property,
        claim,
        method,
        status);
  }
}
