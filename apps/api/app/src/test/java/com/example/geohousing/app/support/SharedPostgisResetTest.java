package com.example.geohousing.app.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Proves the shared-database reset keeps migration state while removing application rows. */
class SharedPostgisResetTest {

  @BeforeAll
  static void migrateSharedDatabase() {
    migrate();
    SharedPostgis.resetApplicationTables();
  }

  @Test
  void resetEmptiesEveryModuleAndKeepsMigrationState() throws Exception {
    try (Connection connection = connection()) {
      List<String> histories = historyTables(connection);
      long historyRows = totalRows(connection, histories);
      long spatialRows = count(connection, "public.spatial_ref_sys");

      assertThat(historyRows).as("the migrations recorded their own history").isPositive();
      assertThat(spatialRows).as("PostGIS ships reference rows").isPositive();

      insertOneRowPerModule(connection);
      assertApplicationRows(connection, 1);

      SharedPostgis.resetApplicationTables();

      assertApplicationRows(connection, 0);
      assertThat(totalRows(connection, histories))
          .as("the reset must not truncate Flyway history")
          .isEqualTo(historyRows);
      assertThat(count(connection, "public.spatial_ref_sys"))
          .as("the reset must not truncate extension-owned tables")
          .isEqualTo(spatialRows);

      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "create table properties.future_migration_table (id int primary key, note text)");
        statement.execute("insert into properties.future_migration_table values (1, 'future')");
      }
      assertThat(count(connection, "properties.future_migration_table")).isEqualTo(1);

      SharedPostgis.resetApplicationTables();

      assertThat(count(connection, "properties.future_migration_table"))
          .as("a table added after the first reset must be discovered by the catalogue query")
          .isZero();

      try (Statement statement = connection.createStatement()) {
        statement.execute("drop table properties.future_migration_table");
      }
    }
  }

  @Test
  void twoRegistrationsPointAtTheSameContainerAndPort() {
    Map<String, Supplier<Object>> first = registerOnce();
    Map<String, Supplier<Object>> second = registerOnce();

    Object firstUrl = first.get("spring.datasource.url").get();
    Object secondUrl = second.get("spring.datasource.url").get();
    PostgreSQLContainer container = SharedPostgis.start();

    assertThat(firstUrl).isEqualTo(secondUrl);
    assertThat(firstUrl.toString())
        .contains(":" + container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/test");
  }

  private static Map<String, Supplier<Object>> registerOnce() {
    Map<String, Supplier<Object>> properties = new HashMap<>();
    DynamicPropertyRegistry registry = (name, supplier) -> properties.put(name, supplier);
    SharedPostgis.register(registry);
    return properties;
  }

  private static void insertOneRowPerModule(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          "insert into identity.account (auth_subject_hash) values ('" + "a".repeat(64) + "')");
      statement.execute("insert into properties.address default values");
      statement.execute(
          "insert into reviews.review (property_id, author_account_id, relationship_type)"
              + " values ('"
              + UUID.randomUUID()
              + "', '"
              + UUID.randomUUID()
              + "', 'CURRENT_RESIDENT')");
      statement.execute(
          "insert into verification.verification_case"
              + " (account_id, property_id, relationship_claim, method) values ('"
              + UUID.randomUUID()
              + "', '"
              + UUID.randomUUID()
              + "', 'CURRENT_RESIDENT', 'INVITATION')");
      statement.execute(
          "insert into moderation.moderation_case (target_type, target_id, trigger_source)"
              + " values ('REVIEW', '"
              + UUID.randomUUID()
              + "', 'REPORT')");
    }
  }

  private static void assertApplicationRows(Connection connection, long expected)
      throws SQLException {
    for (String table :
        List.of(
            "identity.account",
            "properties.address",
            "reviews.review",
            "verification.verification_case",
            "moderation.moderation_case")) {
      assertThat(count(connection, table)).as("%s was reset", table).isEqualTo(expected);
    }
  }

  private static List<String> historyTables(Connection connection) throws SQLException {
    String sql =
        "select format('%I.%I', table_schema, table_name) from information_schema.tables"
            + " where table_schema not in ('pg_catalog', 'information_schema')"
            + " and table_name like 'flyway\\_schema\\_history%' escape '\\'"
            + " order by table_schema, table_name";
    List<String> tables = new ArrayList<>();
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(sql)) {
      while (rows.next()) {
        tables.add(rows.getString(1));
      }
    }
    return tables;
  }

  private static long totalRows(Connection connection, List<String> tables) throws SQLException {
    long total = 0;
    for (String table : tables) {
      total += count(connection, table);
    }
    return total;
  }

  private static long count(Connection connection, String qualifiedTable) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery("select count(*) from " + qualifiedTable)) {
      rows.next();
      return rows.getLong(1);
    }
  }

  private static Connection connection() throws SQLException {
    PostgreSQLContainer container = SharedPostgis.start();
    return DriverManager.getConnection(
        container.getJdbcUrl(), container.getUsername(), container.getPassword());
  }

  private static void migrate() {
    PostgreSQLContainer container = SharedPostgis.start();
    Flyway.configure()
        .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
        .locations("classpath:db/migration/root")
        .table("flyway_schema_history_root")
        .baselineOnMigrate(true)
        .baselineVersion("1")
        .load()
        .migrate();
    moduleFlyway(container, "identity", "2");
    moduleFlyway(container, "properties", "3");
    moduleFlyway(container, "reviews", "4");
    moduleFlyway(container, "verification", "5");
    moduleFlyway(container, "moderation", "6");
  }

  private static void moduleFlyway(PostgreSQLContainer container, String module, String baseline) {
    Flyway.configure()
        .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
        .locations("classpath:db/migration/" + module)
        .schemas(module)
        .defaultSchema(module)
        .baselineOnMigrate(true)
        .baselineVersion(baseline)
        .load()
        .migrate();
  }
}
