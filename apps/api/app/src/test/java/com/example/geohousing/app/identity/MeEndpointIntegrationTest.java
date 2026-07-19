package com.example.geohousing.app.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Exercises {@code /api/me} through the real security filter chain end to end. A stub {@link
 * JwtDecoder} maps a bearer token straight to a {@link Jwt} whose subject is the token string, so
 * the actual chunk-5 {@code IdentityJwtAuthenticationConverter} runs (decode →
 * resolve/auto-provision → account id as principal). This is what a {@code jwt()} post-processor
 * cannot do, and it lets the negative-path matrix (401/403/409/422) be asserted against a real
 * Postgres.
 *
 * <p>The context is shared across methods, so each test uses distinct subjects and pseudonyms to
 * stay independent of ordering.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(MeEndpointIntegrationTest.StubJwtDecoderConfig.class)
class MeEndpointIntegrationTest {

  private static final int UNPROCESSABLE_CONTENT = 422;

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  @TestConfiguration
  static class StubJwtDecoderConfig {

    @Bean
    JwtDecoder jwtDecoder() {
      return token ->
          Jwt.withTokenValue(token)
              .header("alg", "none")
              .subject(token)
              .issuedAt(Instant.parse("2026-07-15T00:00:00Z"))
              .expiresAt(Instant.parse("2999-01-01T00:00:00Z"))
              .build();
    }
  }

  @Test
  void anonymousRequestIsUnauthorized() throws Exception {
    mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void firstAuthenticatedRequestAutoProvisionsTheAccount() throws Exception {
    mockMvc
        .perform(get("/api/me").header("Authorization", bearer("subject-newcomer")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pseudonym").value(Matchers.startsWith("Reviewer-")))
        .andExpect(jsonPath("$.role").value("USER"))
        .andExpect(jsonPath("$.version").value(0));
  }

  @Test
  void updatesTheProfileAndBumpsTheVersion() throws Exception {
    String bearer = bearer("subject-editor");
    provision(bearer);

    mockMvc
        .perform(
            patch("/api/me/profile")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Fresh Name", null, "ka", 0L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pseudonym").value("Fresh Name"))
        .andExpect(jsonPath("$.locale").value("ka"))
        .andExpect(jsonPath("$.version").value(1));

    mockMvc
        .perform(get("/api/me").header("Authorization", bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pseudonym").value("Fresh Name"))
        .andExpect(jsonPath("$.version").value(1));
  }

  @Test
  void staleVersionYieldsConflict() throws Exception {
    String bearer = bearer("subject-staleversion");
    provision(bearer);

    mockMvc
        .perform(
            patch("/api/me/profile")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Stale Editor", null, "en", 7L)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PROFILE_VERSION_CONFLICT"));
  }

  @Test
  void duplicatePseudonymIsUnprocessable() throws Exception {
    String owner = bearer("subject-owner");
    provision(owner);
    mockMvc
        .perform(
            patch("/api/me/profile")
                .header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Contested Name", null, "en", 0L)))
        .andExpect(status().isOk());

    String rival = bearer("subject-rival");
    provision(rival);
    mockMvc
        .perform(
            patch("/api/me/profile")
                .header("Authorization", rival)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Contested Name", null, "en", 0L)))
        .andExpect(status().is(UNPROCESSABLE_CONTENT))
        .andExpect(jsonPath("$.code").value("PSEUDONYM_TAKEN"));
  }

  @Test
  void malformedPseudonymIsUnprocessable() throws Exception {
    String bearer = bearer("subject-malformed");
    provision(bearer);

    mockMvc
        .perform(
            patch("/api/me/profile")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("bad!name", null, "en", 0L)))
        .andExpect(status().is(UNPROCESSABLE_CONTENT))
        .andExpect(jsonPath("$.code").value("INVALID_PSEUDONYM"));
  }

  @Test
  void restrictedAccountCannotUpdateProfile() throws Exception {
    String bearer = bearer("subject-restricted");
    UUID accountId = UUID.fromString(accountIdOf(bearer));

    jdbcTemplate.update(
        "insert into identity.user_restriction"
            + " (id, account_id, scope, reason, start_at, appeal_status)"
            + " values (?, ?, 'ACCOUNT_WIDE', 'abuse', ?, 'NONE')",
        UUID.randomUUID(),
        accountId,
        Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));

    mockMvc
        .perform(
            patch("/api/me/profile")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Blocked Edit", null, "en", 0L)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCOUNT_RESTRICTED"));
  }

  private static String bearer(String subject) {
    return "Bearer " + subject;
  }

  private void provision(String bearer) throws Exception {
    mockMvc.perform(get("/api/me").header("Authorization", bearer)).andExpect(status().isOk());
  }

  private String accountIdOf(String bearer) throws Exception {
    String body =
        mockMvc
            .perform(get("/api/me").header("Authorization", bearer))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.accountId");
  }

  private static String updateBody(
      String pseudonym, String avatarUrl, String locale, long version) {
    String avatar = avatarUrl == null ? "null" : "\"" + avatarUrl + "\"";
    return "{\"pseudonym\":\""
        + pseudonym
        + "\",\"avatarUrl\":"
        + avatar
        + ",\"locale\":\""
        + locale
        + "\",\"version\":"
        + version
        + "}";
  }
}
