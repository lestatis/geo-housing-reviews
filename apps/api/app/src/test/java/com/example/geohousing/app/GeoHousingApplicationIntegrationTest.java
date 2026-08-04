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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class GeoHousingApplicationIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void flywayMigrationAppliedAndPostgisExtensionEnabled() {
    Integer migrationCount =
        jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history_root where script = 'V1__init.sql' and success = true",
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

  /**
   * The security chain must fail closed: an unauthenticated request to any non-health path is
   * rejected by the resource-server filter (401) before it can reach a handler. This guards the
   * chunk-5 {@code SecurityConfiguration} against silently permitting anonymous access; end-to-end
   * authenticated behavior arrives with chunk 6's endpoints.
   */
  @Test
  void protectedPathRejectsAnonymousRequests() throws Exception {
    mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
  }
}
