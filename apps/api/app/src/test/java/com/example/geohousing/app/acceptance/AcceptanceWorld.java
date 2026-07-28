package com.example.geohousing.app.acceptance;

import io.cucumber.spring.CucumberContextConfiguration;
import io.cucumber.spring.ScenarioScope;
import java.time.Instant;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The Spring context every scenario runs against: the whole application, a real Postgres, and the
 * same stub {@link JwtDecoder} the JUnit endpoint tests use, so scenarios exercise the real
 * security chain rather than a bypass.
 *
 * <p>One container serves the entire suite. The fourteen JUnit endpoint test classes each start
 * their own; consolidating them here as they migrate is the main speed argument for the migration.
 */
@CucumberContextConfiguration
@SpringBootTest
@AutoConfigureMockMvc
@Import(AcceptanceWorld.AcceptanceConfig.class)
public class AcceptanceWorld {

  @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  static {
    // Started here rather than by @Testcontainers: Cucumber owns the lifecycle of the class that
    // carries @CucumberContextConfiguration, and the JUnit extension does not run for it.
    POSTGRES.start();
  }

  @TestConfiguration
  static class AcceptanceConfig {

    /**
     * Scenario-scoped, because Cucumber shares one Spring context across every scenario. A
     * singleton here would let one scenario's actors and ids leak into the next, and the failure
     * would look like a flaky test rather than shared state.
     */
    @Bean
    @ScenarioScope
    ScenarioState scenarioState() {
      return new ScenarioState();
    }

    @Bean
    @ScenarioScope
    TestApi testApi(MockMvc mockMvc, JdbcTemplate jdbcTemplate, ScenarioState scenarioState) {
      return new TestApi(mockMvc, jdbcTemplate, scenarioState);
    }

    @Bean
    JwtDecoder jwtDecoder() {
      return token ->
          Jwt.withTokenValue(token)
              .header("alg", "none")
              .subject(token)
              .issuedAt(Instant.parse("2026-07-28T00:00:00Z"))
              .expiresAt(Instant.parse("2999-01-01T00:00:00Z"))
              .build();
    }
  }
}
