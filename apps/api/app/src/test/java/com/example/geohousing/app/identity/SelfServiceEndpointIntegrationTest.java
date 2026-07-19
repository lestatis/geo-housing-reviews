package com.example.geohousing.app.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end export and deletion through the real chain (stub {@link JwtDecoder}, as in the other
 * identity endpoint tests). Proves the idempotency-key contract, that deletion closes/anonymizes
 * the data, and — the key acceptance criterion — that a deleted subject cannot be re-provisioned.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(SelfServiceEndpointIntegrationTest.StubJwtDecoderConfig.class)
class SelfServiceEndpointIntegrationTest {

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
              .claim("email", token + "@example.com")
              .issuedAt(Instant.parse("2026-07-15T00:00:00Z"))
              .expiresAt(Instant.parse("2999-01-01T00:00:00Z"))
              .build();
    }
  }

  @Test
  void exportReturnsTheUsersDataIncludingEmailAndRecordsTheRequest() throws Exception {
    String bearer = bearer("subject-export");
    UUID accountId = UUID.fromString(accountIdOf(bearer));

    mockMvc
        .perform(
            post("/api/me/export").header("Authorization", bearer).header("Idempotency-Key", "k-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.account.email").value("subject-export@example.com"))
        .andExpect(jsonPath("$.profile.pseudonym").exists());

    assertThat(requestCount(accountId, "EXPORT")).isEqualTo(1);
  }

  @Test
  void deleteClosesTheAccountAndAnonymizesTheProfile() throws Exception {
    String bearer = bearer("subject-delete");
    UUID accountId = UUID.fromString(accountIdOf(bearer));

    mockMvc
        .perform(delete("/api/me").header("Authorization", bearer).header("Idempotency-Key", "k-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CLOSED"));

    Map<String, Object> account =
        jdbcTemplate.queryForMap(
            "select status, email from identity.account where id = ?", accountId);
    assertThat(account.get("status")).isEqualTo("CLOSED");
    assertThat(account.get("email")).isNull();

    Map<String, Object> profile =
        jdbcTemplate.queryForMap(
            "select pseudonym, avatar_url from identity.public_profile where account_id = ?",
            accountId);
    assertThat((String) profile.get("pseudonym")).startsWith("del-");
    assertThat(profile.get("avatar_url")).isNull();
    assertThat(requestCount(accountId, "DELETE")).isEqualTo(1);
  }

  @Test
  void aDeletedSubjectCannotBeReProvisioned() throws Exception {
    String bearer = bearer("subject-noresurrect");
    accountIdOf(bearer); // provision

    mockMvc
        .perform(delete("/api/me").header("Authorization", bearer).header("Idempotency-Key", "k-1"))
        .andExpect(status().isOk());

    // The same subject authenticates again, but the account is closed — no silent recreation.
    mockMvc
        .perform(get("/api/me").header("Authorization", bearer))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void reusingAKeyForADifferentOperationIsAConflict() throws Exception {
    String bearer = bearer("subject-keyreuse");
    accountIdOf(bearer);
    mockMvc
        .perform(
            post("/api/me/export")
                .header("Authorization", bearer)
                .header("Idempotency-Key", "shared"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            delete("/api/me").header("Authorization", bearer).header("Idempotency-Key", "shared"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
  }

  @Test
  void aMissingIdempotencyKeyIsRejected() throws Exception {
    String bearer = bearer("subject-nokey");
    accountIdOf(bearer);

    mockMvc
        .perform(post("/api/me/export").header("Authorization", bearer))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
  }

  @Test
  void repeatingAnExportWithTheSameKeyIsIdempotent() throws Exception {
    // A repeated DELETE is not reachable through the endpoint — a deleted account is 401'd at auth
    // (no resurrection), which is asserted separately. Export leaves the account open, so it is the
    // reachable endpoint-level replay: same key twice succeeds and records exactly one request.
    String bearer = bearer("subject-replayexport");
    UUID accountId = UUID.fromString(accountIdOf(bearer));

    mockMvc
        .perform(
            post("/api/me/export").header("Authorization", bearer).header("Idempotency-Key", "k-1"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/me/export").header("Authorization", bearer).header("Idempotency-Key", "k-1"))
        .andExpect(status().isOk());

    assertThat(requestCount(accountId, "EXPORT")).isEqualTo(1);
  }

  private static String bearer(String subject) {
    return "Bearer " + subject;
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

  private Integer requestCount(UUID accountId, String type) {
    return jdbcTemplate.queryForObject(
        "select count(*) from identity.self_service_request"
            + " where account_id = ? and request_type = ?",
        Integer.class,
        accountId,
        type);
  }
}
