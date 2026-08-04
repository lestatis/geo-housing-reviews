package com.example.geohousing.app.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

/**
 * One Flyway instance per module, each with its own history table in its own schema.
 *
 * <p>Every module numbers its migrations in a reserved range — root 1, identity 2, properties 3,
 * reviews 4, verification 5, moderation 6. With a single shared history that ordering is a trap: a
 * new identity migration is numbered <em>below</em> every migration the other modules have already
 * applied, so Flyway refuses it as out of order and the application will not start. It never showed
 * up in tests because a Testcontainers database is empty, where any order is in order. The first
 * upgrade of a real database would have hit it.
 *
 * <p>Giving each module its own history makes a module's migrations ordered only against its own,
 * which is what the module boundary already claims: {@code docs/ARCHITECTURE.md} says a module owns
 * its tables and its migrations, and now it owns the record of them too.
 *
 * <p>The modules are independent of each other — no module's migrations reference another module's
 * tables — so they may run in any order. The one real dependency is the root migration, which
 * installs PostGIS before properties needs it; hence {@link DependsOn} on every module instance.
 */
@Configuration
class ModuleMigrationConfiguration {

  /** The history table the old single-instance arrangement wrote to. */
  private static final String SHARED_HISTORY = "flyway_schema_history";

  /**
   * Shared setup — the extensions every schema needs. Lives in {@code public}, and everything else
   * waits for it.
   *
   * <p>Its history goes in a table of its own rather than the {@code flyway_schema_history} the old
   * shared arrangement used, because that table still holds every module's rows on an existing
   * database. Sharing it would leave the root instance with the same disease as the modules: a new
   * root migration numbered {@code 1.1} sorts below the {@code 6.1} recorded there and is refused.
   *
   * <p>No baseline, and none needed: on an existing database this instance starts from an empty
   * history and re-runs both root migrations, which are {@code CREATE EXTENSION IF NOT EXISTS} and
   * therefore no-ops. <strong>Root migrations must stay idempotent for that to hold.</strong> A
   * root migration that cannot be run twice needs a baseline here, or a different transition.
   */
  @Bean(initMethod = "migrate")
  Flyway rootFlyway(DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration/root")
        .table("flyway_schema_history_root")
        .baselineOnMigrate(true)
        .baselineVersion(versionAlreadyApplied(dataSource, "1"))
        .load();
  }

  @Bean(initMethod = "migrate")
  @DependsOn("rootFlyway")
  Flyway identityFlyway(DataSource dataSource) {
    return moduleFlyway(dataSource, "identity", "2");
  }

  @Bean(initMethod = "migrate")
  @DependsOn("rootFlyway")
  Flyway propertiesFlyway(DataSource dataSource) {
    return moduleFlyway(dataSource, "properties", "3");
  }

  @Bean(initMethod = "migrate")
  @DependsOn("rootFlyway")
  Flyway reviewsFlyway(DataSource dataSource) {
    return moduleFlyway(dataSource, "reviews", "4");
  }

  @Bean(initMethod = "migrate")
  @DependsOn("rootFlyway")
  Flyway verificationFlyway(DataSource dataSource) {
    return moduleFlyway(dataSource, "verification", "5");
  }

  @Bean(initMethod = "migrate")
  @DependsOn("rootFlyway")
  Flyway moderationFlyway(DataSource dataSource) {
    return moduleFlyway(dataSource, "moderation", "6");
  }

  private static Flyway moduleFlyway(DataSource dataSource, String module, String range) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration/" + module)
        // The history table goes in the module's own schema, which is what makes the ordering local
        // to the module. Flyway creates the schema if it is missing, so a fresh database needs no
        // preparation — the CREATE SCHEMA in each module's first migration stays as documentation.
        .schemas(module)
        .defaultSchema(module)
        .baselineOnMigrate(true)
        .baselineVersion(versionAlreadyApplied(dataSource, range))
        .load();
  }

  /**
   * How far this module had already got under the old shared history.
   *
   * <p>Read rather than hardcoded, because two prior states exist and they need different answers:
   * a database that stopped at {@code 6.1} never received identity's {@code 2.7}, while one created
   * after that chunk shipped has it. A fixed baseline is wrong for one of them, and wrong in the
   * worst direction — re-running a migration is not always harmless. {@code V2.7} narrows a CHECK
   * that {@code V2.8} widens, so replaying the pair against rows only the later one permits fails
   * outright.
   *
   * <p>Rows are matched by version prefix, which is what the per-module numbering gives us, and
   * taken in installation order — string comparison would sort {@code 2.10} below {@code 2.9}. A
   * database with no shared history is a fresh one and has nothing to skip.
   */
  private static String versionAlreadyApplied(DataSource dataSource, String range) {
    String sql =
        "select version from "
            + SHARED_HISTORY
            + " where success = true and version like ? order by installed_rank desc limit 1";
    try (java.sql.Connection connection = dataSource.getConnection();
        java.sql.PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, range + ".%");
      try (java.sql.ResultSet rows = statement.executeQuery()) {
        return rows.next() ? rows.getString(1) : range;
      }
    } catch (java.sql.SQLException noSharedHistory) {
      // No such table: this database never ran the old arrangement, so there is nothing to skip.
      // baselineOnMigrate only fires on a non-empty schema anyway, which a fresh one is not.
      return range;
    }
  }

  /**
   * Stops JPA validating the schema before the migrations that create it have run.
   *
   * <p>Spring Boot's own Flyway auto-configuration does exactly this for its single instance; with
   * auto-configuration off, the dependency has to be declared here or {@code ddl-auto: validate}
   * races the migrations and fails on a cold database.
   */
  @Bean
  static EntityManagerFactoryDependsOnPostProcessor migrationsBeforeEntityManagerFactory() {
    return new EntityManagerFactoryDependsOnPostProcessor(
        "rootFlyway",
        "identityFlyway",
        "propertiesFlyway",
        "reviewsFlyway",
        "verificationFlyway",
        "moderationFlyway") {};
  }
}
