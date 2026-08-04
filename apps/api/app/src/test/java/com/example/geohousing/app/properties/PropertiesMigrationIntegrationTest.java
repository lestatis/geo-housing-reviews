package com.example.geohousing.app.properties;

import static org.assertj.core.api.Assertions.assertThat;
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
class PropertiesMigrationIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void propertyCatalogueMigrationApplied() {
    Integer applied =
        jdbcTemplate.queryForObject(
            "select count(*) from properties.flyway_schema_history where version = '3.1' and success = true",
            Integer.class);
    assertThat(applied).isEqualTo(1);
  }

  @Test
  void propertyTablesExist() {
    for (String table : new String[] {"address", "property", "property_alias", "property_source"}) {
      Integer count =
          jdbcTemplate.queryForObject(
              "select count(*) from information_schema.tables"
                  + " where table_schema = 'properties' and table_name = ?",
              Integer.class,
              table);
      assertThat(count).as("table properties.%s exists", table).isEqualTo(1);
    }
  }

  @Test
  void propertyTypeAndStatusRejectInvalidValues() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into properties.property (type, canonical_name, created_by)"
                        + " values ('CASTLE', 'X', ?)",
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into properties.property (type, canonical_name, created_by, status)"
                        + " values ('BUILDING', 'X', ?, 'ARCHIVED')",
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aMergeTargetRequiresMergedStatus() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into properties.property"
                        + " (type, canonical_name, created_by, status, merged_into_property_id)"
                        + " values ('BUILDING', 'X', ?, 'ACTIVE', ?)",
                    UUID.randomUUID(),
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aBlankCanonicalNameIsRejected() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into properties.property (type, canonical_name, created_by)"
                        + " values ('BUILDING', '   ', ?)",
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
