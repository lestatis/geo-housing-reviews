package com.example.geohousing.app.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.app.support.SharedPostgis;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@ExtendWith(SharedPostgis.ResetBeforeClass.class)
@SpringBootTest
class PropertiesMigrationIntegrationTest {

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    SharedPostgis.register(registry);
  }

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
