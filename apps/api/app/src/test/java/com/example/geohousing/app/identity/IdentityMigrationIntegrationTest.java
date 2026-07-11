package com.example.geohousing.app.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
  }
}
