package com.example.geohousing.app.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
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
class IdentityMigrationIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void identityMigrationsAppliedSuccessfully() {
    Integer accountMigration =
        jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where version = '2.1' and success = true",
            Integer.class);
    assertThat(accountMigration).isEqualTo(1);

    Integer profileMigration =
        jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where version = '2.2' and success = true",
            Integer.class);
    assertThat(profileMigration).isEqualTo(1);

    Integer restrictionMigration =
        jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where version = '2.3' and success = true",
            Integer.class);
    assertThat(restrictionMigration).isEqualTo(1);

    Integer subjectHashFormatMigration =
        jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where version = '2.4' and success = true",
            Integer.class);
    assertThat(subjectHashFormatMigration).isEqualTo(1);
  }

  /**
   * V2.3 renamed {@code auth_subject} to {@code auth_subject_hash} and retyped it to CHAR(64)
   * without validating the existing values, so a raw OIDC subject — short enough to be silently
   * blank-padded to 64 characters — would survive as if it were a hash. That is the reversible
   * identifier ADR-0006 exists to remove, so the format constraint added by V2.4 must reject it.
   */
  @Test
  void accountRejectsAnAuthSubjectHashThatIsNotAHash() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into identity.account (auth_subject_hash) values (?)",
                    "google-oauth2|1092847561"))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into identity.account (auth_subject_hash) values (?)", "Z".repeat(64)))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatCode(
            () ->
                jdbcTemplate.update(
                    "insert into identity.account (auth_subject_hash) values (?)",
                    "a1b2c3d4".repeat(8)))
        .doesNotThrowAnyException();
  }

  @Test
  void identityTablesExist() {
    Integer accountTableCount =
        jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables"
                + " where table_schema = 'identity' and table_name = 'account'",
            Integer.class);
    assertThat(accountTableCount).isEqualTo(1);

    Integer profileTableCount =
        jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables"
                + " where table_schema = 'identity' and table_name = 'public_profile'",
            Integer.class);
    assertThat(profileTableCount).isEqualTo(1);

    Integer restrictionTableCount =
        jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables"
                + " where table_schema = 'identity' and table_name = 'user_restriction'",
            Integer.class);
    assertThat(restrictionTableCount).isEqualTo(1);

    Integer subjectHashLength =
        jdbcTemplate.queryForObject(
            "select character_maximum_length from information_schema.columns"
                + " where table_schema = 'identity' and table_name = 'account'"
                + " and column_name = 'auth_subject_hash'",
            Integer.class);
    assertThat(subjectHashLength).isEqualTo(64);
  }

  @Test
  void accountRoleAndStatusRejectInvalidValues() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into identity.account (auth_subject_hash, role) values (?, 'OWNER')",
                    "a".repeat(64)))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into identity.account (auth_subject_hash, status) values (?, 'DELETED')",
                    "b".repeat(64)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void userRestrictionRejectsInvalidScopeAndDates() {
    UUID accountId =
        jdbcTemplate.queryForObject(
            "insert into identity.account (auth_subject_hash) values (?) returning id",
            UUID.class,
            "c".repeat(64));

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into identity.user_restriction"
                        + " (id, account_id, scope, reason, start_at, appeal_status)"
                        + " values (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(),
                    accountId,
                    "PROPERTY",
                    "abuse",
                    Timestamp.valueOf("2026-01-01 00:00:00"),
                    "NONE"))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into identity.user_restriction"
                        + " (id, account_id, scope, reason, start_at, end_at, appeal_status)"
                        + " values (?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(),
                    accountId,
                    "ACCOUNT_WIDE",
                    "abuse",
                    Timestamp.valueOf("2026-01-02 00:00:00"),
                    Timestamp.valueOf("2026-01-01 00:00:00"),
                    "NONE"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
