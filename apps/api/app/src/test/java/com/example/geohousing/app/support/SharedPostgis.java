package com.example.geohousing.app.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One PostgreSQL container and one database for every integration-test class in a JVM.
 *
 * <p>Before this harness each class started its own container, which paid for image startup and
 * every module's Flyway migrations once per class. The database is now shared, so isolation is
 * explicit: {@link ResetBeforeClass} truncates all application tables before the class's first
 * test. Flyway history and extension-owned tables (for example {@code public.spatial_ref_sys}) are
 * kept, because migrations must stay applied and PostGIS must keep working.
 *
 * <p>The container is deliberately never stopped by a test extension. Ryuk removes it when the JVM
 * exits, after Spring has closed its cached contexts, so Hikari never validates a connection
 * against a database whose container has already gone away.
 */
public final class SharedPostgis {

  private static final DockerImageName IMAGE =
      DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres");

  private static PostgreSQLContainer container;

  private SharedPostgis() {}

  /**
   * The pool size for an ordinary class. Up to 32 cached contexts in one JVM share the database, so
   * the pool stays small; {@code minimum-idle=0} keeps idle pools from holding connections.
   */
  private static final int DEFAULT_POOL_SIZE = 5;

  /** Registers the shared database and the test-only connection and scheduling settings. */
  public static void register(DynamicPropertyRegistry registry) {
    register(registry, DEFAULT_POOL_SIZE);
  }

  /**
   * As {@link #register(DynamicPropertyRegistry)}, for a class whose test needs more concurrent
   * connections than the default pool allows.
   */
  public static void register(DynamicPropertyRegistry registry, int maximumPoolSize) {
    PostgreSQLContainer shared = start();
    registry.add("spring.datasource.url", shared::getJdbcUrl);
    registry.add("spring.datasource.username", shared::getUsername);
    registry.add("spring.datasource.password", shared::getPassword);
    registry.add("spring.datasource.hikari.maximum-pool-size", () -> maximumPoolSize);
    registry.add("spring.datasource.hikari.minimum-idle", () -> 0);
    // A cached context from an earlier class must not sweep this class's rows. The one sweep each
    // context fires as it starts is unchanged from the per-class-container arrangement.
    registry.add("verification.expiry.sweep-fixed-delay", () -> "PT24H");
    registry.add("verification.evidence.retention.sweep-fixed-delay", () -> "PT24H");
  }

  /** Starts the JVM-wide container on first use and returns it thereafter. */
  static synchronized PostgreSQLContainer start() {
    if (container == null) {
      PostgreSQLContainer started =
          new PostgreSQLContainer(IMAGE)
              .withCommand(
                  "postgres",
                  "-c",
                  "max_connections=200",
                  "-c",
                  "fsync=off",
                  "-c",
                  "synchronous_commit=off",
                  "-c",
                  "full_page_writes=off");
      started.start();
      container = started;
    }
    return container;
  }

  /**
   * Empties every application table in one statement.
   *
   * <p>Tables are discovered from {@code pg_catalog} rather than listed here, so a table added by a
   * future migration is covered automatically. Flyway history tables and extension-owned tables are
   * excluded. On the first class of a JVM there are no application tables yet, and the reset does
   * nothing.
   */
  static void resetApplicationTables() {
    PostgreSQLContainer shared = start();
    try (Connection connection =
        DriverManager.getConnection(
            shared.getJdbcUrl(), shared.getUsername(), shared.getPassword())) {
      List<String> tables = applicationTables(connection);
      if (tables.isEmpty()) {
        return;
      }
      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "truncate table " + String.join(", ", tables) + " restart identity cascade");
      }
    } catch (SQLException failed) {
      throw new IllegalStateException("could not reset the shared test database", failed);
    }
  }

  private static List<String> applicationTables(Connection connection) throws SQLException {
    String sql =
        "select format('%I.%I', schema.nspname, relation.relname)"
            + " from pg_catalog.pg_class relation"
            + " join pg_catalog.pg_namespace schema on schema.oid = relation.relnamespace"
            + " where relation.relkind = 'r'"
            + " and schema.nspname not in ('pg_catalog', 'information_schema')"
            + " and schema.nspname not like 'pg\\_%' escape '\\'"
            + " and relation.relname <> 'flyway_schema_history'"
            + " and relation.relname not like 'flyway\\_schema\\_history\\_%' escape '\\'"
            + " and not exists ("
            + "   select 1 from pg_catalog.pg_depend dependency"
            + "   where dependency.classid = 'pg_catalog.pg_class'::regclass"
            + "   and dependency.objid = relation.oid"
            + "   and dependency.deptype = 'e')"
            + " order by schema.nspname, relation.relname";
    List<String> tables = new ArrayList<>();
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(sql)) {
      while (rows.next()) {
        tables.add(rows.getString(1));
      }
    }
    return tables;
  }

  /**
   * Resets the shared database before the class's first test, ahead of Spring's context lifecycle.
   *
   * <p>JUnit registers class-level {@code @ExtendWith} extensions in declaration order and does not
   * honour {@code @Order} on the extension class. Declare this extension directly on the test class
   * before {@code @SpringBootTest}, whose meta-annotated {@code SpringExtension} creates the
   * context in its own {@code beforeAll}. An annotation-order regression would move the reset after
   * context startup; {@code SharedPostgisResetTest} and the pilot classes would still run, but the
   * timing guarantee recorded in plan 020 would be lost.
   */
  public static final class ResetBeforeClass implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
      resetApplicationTables();
    }
  }
}
