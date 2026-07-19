package com.example.geohousing.app.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
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
 * Proves the admin RBAC gate and the audit trail end to end through the real security chain (stub
 * {@link JwtDecoder}, as in {@code MeEndpointIntegrationTest}). An account is promoted to {@code
 * ADMIN} with a direct SQL update, mirroring the manual first-admin bootstrap (there is no
 * self-service role escalation by design).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(AdminAccountEndpointIntegrationTest.StubJwtDecoderConfig.class)
class AdminAccountEndpointIntegrationTest {

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
  void anonymousAdminRequestIsUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/admin/accounts/" + UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void nonAdminIsForbidden() throws Exception {
    String user = bearer("subject-plain-user");
    provision(user); // stays role USER

    mockMvc
        .perform(get("/api/admin/accounts/" + UUID.randomUUID()).header("Authorization", user))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminViewsAnAccountWithoutExposingTheSubjectHashAndTheAccessIsAudited() throws Exception {
    UUID targetId = UUID.fromString(accountIdOf(bearer("subject-target")));
    String admin = bearer("subject-admin-viewer");
    UUID adminId = UUID.fromString(accountIdOf(admin));
    promoteToAdmin(adminId);

    String body =
        mockMvc
            .perform(get("/api/admin/accounts/" + targetId).header("Authorization", admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(targetId.toString()))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    // The credential-derived hash must never appear in an admin (or any) response.
    assertThat(body).doesNotContain("authSubjectHash");
    assertThat(body.toLowerCase()).doesNotContain("subject");

    Integer audited =
        jdbcTemplate.queryForObject(
            "select count(*) from identity.admin_audit_event"
                + " where admin_account_id = ? and target_account_id = ?"
                + " and action = 'VIEW_ACCOUNT' and outcome = 'FOUND'",
            Integer.class,
            adminId,
            targetId);
    assertThat(audited).isEqualTo(1);
  }

  @Test
  void adminLookupOfUnknownAccountIsNotFoundAndStillAudited() throws Exception {
    String admin = bearer("subject-admin-miss");
    UUID adminId = UUID.fromString(accountIdOf(admin));
    promoteToAdmin(adminId);
    UUID missing = UUID.randomUUID();

    mockMvc
        .perform(get("/api/admin/accounts/" + missing).header("Authorization", admin))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));

    Integer audited =
        jdbcTemplate.queryForObject(
            "select count(*) from identity.admin_audit_event"
                + " where admin_account_id = ? and target_account_id = ? and outcome = 'NOT_FOUND'",
            Integer.class,
            adminId,
            missing);
    assertThat(audited).isEqualTo(1);
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

  private void promoteToAdmin(UUID accountId) {
    jdbcTemplate.update("update identity.account set role = 'ADMIN' where id = ?", accountId);
  }
}
