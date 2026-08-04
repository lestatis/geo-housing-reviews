package com.example.geohousing.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
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
 * Upgrading a database that was migrated the old way — one Flyway instance over every module, one
 * {@code public.flyway_schema_history}.
 *
 * <p>This is the case that fails without per-module histories, and the reason it was never caught:
 * every other integration test starts from an empty container, where a global ordering is trivially
 * satisfied. Here the container is deliberately brought to the previous state first, so the
 * application starts against a database that already carries {@code 6.1} and is asked to apply
 * identity's {@code 2.7} and {@code 2.8} — which a single shared history refuses as out of order.
 *
 * <p>The old arrangement is reproduced by hand rather than kept in the codebase: it is what
 * production looks like today, not something the build should still be able to produce.
 */
@Testcontainers
@SpringBootTest
class MigrationHistorySplitIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
              DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
          .withReuse(false);

  @Autowired private JdbcTemplate jdbcTemplate;

  /**
   * Brings the container to the pre-split state before Spring migrates it.
   *
   * <p>Runs as a static initialiser on the container so it happens before the application context
   * starts — the point is that the application meets a database somebody else already migrated.
   */
  static {
    POSTGRES.start();
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        // Every location at once, into a single history in public: the arrangement this change
        // replaces. Stopped at the versions that existed before identity 2.7 arrived.
        .locations(
            "classpath:db/migration/root",
            "classpath:db/migration/identity",
            "classpath:db/migration/properties",
            "classpath:db/migration/reviews",
            "classpath:db/migration/verification",
            "classpath:db/migration/moderation")
        .load()
        .migrate();
    rewindPastTheMigrationsThatCameLater();
  }

  /**
   * Puts the container back to how a database looked before identity's {@code 2.7} and {@code 2.8}
   * existed.
   *
   * <p>Flyway's {@code target} cannot express "everything except these two" — it is a ceiling, and
   * both sort below the {@code 6.1} the other modules need. So they are applied and then undone:
   * the rows leave the shared history and the CHECK goes back to the vocabulary {@code V2.5} left.
   * That is the state a real deployment is in today, and the one where {@code 2.7} sorting below
   * {@code 6.1} makes it unapplyable.
   */
  private static void rewindPastTheMigrationsThatCameLater() {
    try (java.sql.Connection connection =
            java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        java.sql.Statement statement = connection.createStatement()) {
      statement.execute("delete from flyway_schema_history where version in ('2.7', '2.8')");
      statement.execute(
          "alter table identity.admin_audit_event drop constraint admin_audit_event_action_check");
      statement.execute(
          "alter table identity.admin_audit_event add constraint admin_audit_event_action_check"
              + " check (action in ('VIEW_ACCOUNT'))");
    } catch (java.sql.SQLException failed) {
      throw new IllegalStateException("could not build the pre-2.7 state", failed);
    }
  }

  @Test
  void theApplicationStartsAgainstADatabaseMigratedUnderTheOldSharedHistory() {
    // Reaching this assertion at all is most of the test: the context could not refresh otherwise.
    assertThat(jdbcTemplate.queryForObject("select 1", Integer.class)).isEqualTo(1);
  }

  @Test
  void eachModuleGainsItsOwnHistoryWithoutReapplyingWhatWasAlreadyThere() {
    for (String module :
        new String[] {"identity", "properties", "reviews", "verification", "moderation"}) {
      assertThat(historyExists(module)).as("%s keeps its own history", module).isTrue();
    }

    // Baselined rather than re-run: the tables were already there, and applying 2.1 again would
    // fail on an existing table long before it could do any damage. The baseline lands on 2.6 —
    // read from the shared history, not assumed — because that is where this database stopped.
    assertThat(appliedVersions("identity")).contains("2.6");
  }

  @Test
  void migrationsBelowTheOthersAreFinallyApplied() {
    // The whole point. A single shared history refuses these as out of order, because 2.7 sorts
    // below the 6.1 already applied.
    assertThat(appliedVersions("identity")).contains("2.7", "2.8");

    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from information_schema.check_constraints"
                    + " where constraint_name = 'admin_audit_event_action_check'"
                    + " and check_clause like '%LIFT_RESTRICTION%'",
                Integer.class))
        .as("V2.8 really ran rather than merely being recorded")
        .isEqualTo(1);
  }

  @Test
  void theRootInstanceIsNotHeldBackByTheOldSharedHistory() {
    // The root instance had the same disease as the modules until it was given a history of its
    // own: a new root migration sorts below the 6.1 the old shared table already records, and was
    // refused. Its migrations are idempotent, so starting from an empty history costs nothing.
    assertThat(
            jdbcTemplate.queryForList(
                "select version from flyway_schema_history_root where success = true",
                String.class))
        // Only the new one is asserted. Whether Flyway also writes an explicit BASELINE row depends
        // on what else happens to live in `public`, which is not the claim being made here.
        .contains("1.1");
  }

  @Test
  void theSharedSetupStaysWhereEverySchemaCanSeeIt() {
    // pg_trgm has to live in public: a module migrating inside its own schema would otherwise
    // install it there, and catalogue search would stop matching without any error.
    assertThat(
            jdbcTemplate.queryForObject(
                "select n.nspname from pg_extension e"
                    + " join pg_namespace n on n.oid = e.extnamespace"
                    + " where e.extname = 'pg_trgm'",
                String.class))
        .isEqualTo("public");
  }

  private boolean historyExists(String schema) {
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables"
                + " where table_schema = ? and table_name = 'flyway_schema_history'",
            Integer.class,
            schema);
    return count != null && count == 1;
  }

  private java.util.List<String> appliedVersions(String schema) {
    return jdbcTemplate.queryForList(
        "select version from " + schema + ".flyway_schema_history where success = true",
        String.class);
  }
}
