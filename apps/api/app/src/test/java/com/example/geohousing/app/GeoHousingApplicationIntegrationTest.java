package com.example.geohousing.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class GeoHousingApplicationIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void flywayMigrationAppliedAndPostgisExtensionEnabled() {
    Integer migrationCount =
        jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where script = 'V1__init.sql' and success = true",
            Integer.class);
    assertThat(migrationCount).isEqualTo(1);

    Integer postgisCount =
        jdbcTemplate.queryForObject(
            "select count(*) from pg_extension where extname = 'postgis'", Integer.class);
    assertThat(postgisCount).isEqualTo(1);
  }

  @Test
  void healthEndpointReportsUp() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("UP")));
  }
}
