package com.example.geohousing.app.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Admin lifecycle actions end to end: the ROLE_ADMIN gate, the state transitions, optimistic
 * concurrency, and the append-only audit trail. An account is promoted to ADMIN by SQL, mirroring
 * the manual first-admin bootstrap.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(AdminPropertyEndpointIntegrationTest.StubJwtDecoderConfig.class)
class AdminPropertyEndpointIntegrationTest {

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
              .issuedAt(Instant.parse("2026-07-20T00:00:00Z"))
              .expiresAt(Instant.parse("2999-01-01T00:00:00Z"))
              .build();
    }
  }

  @Test
  void anonymousAndNonAdminCallersAreRejected() throws Exception {
    String user = bearer("prop-admin-plainuser");
    String propertyId = createProperty(user, "Gate Tower " + UUID.randomUUID());

    mockMvc
        .perform(
            post("/api/admin/properties/" + propertyId + "/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0}"))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/api/admin/properties/" + propertyId + "/activate")
                .header("Authorization", user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminActivatesAPropertyAndTheActionIsAudited() throws Exception {
    String admin = adminBearer("prop-admin-activate");
    String propertyId = createProperty(admin, "Activate Tower " + UUID.randomUUID());

    mockMvc
        .perform(
            post("/api/admin/properties/" + propertyId + "/activate")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    assertThat(storedStatus(propertyId)).isEqualTo("ACTIVE");
    assertThat(auditCount(propertyId, "ACTIVATE", "APPLIED")).isEqualTo(1);
  }

  @Test
  void adminHidesAProperty() throws Exception {
    String admin = adminBearer("prop-admin-hide");
    String propertyId = createProperty(admin, "Hide Tower " + UUID.randomUUID());

    mockMvc
        .perform(
            post("/api/admin/properties/" + propertyId + "/hide")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("HIDDEN"));

    assertThat(storedStatus(propertyId)).isEqualTo("HIDDEN");
    assertThat(auditCount(propertyId, "HIDE", "APPLIED")).isEqualTo(1);
  }

  @Test
  void adminMergesAPropertyIntoAnother() throws Exception {
    String admin = adminBearer("prop-admin-merge");
    // The merge target must be a real property: merged_into_property_id has a foreign key (V3.1).
    String target = createProperty(admin, "Merge Target " + UUID.randomUUID());
    String source = createProperty(admin, "Merge Source " + UUID.randomUUID());

    mockMvc
        .perform(
            post("/api/admin/properties/" + source + "/merge")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"targetPropertyId\":\"" + target + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("MERGED"))
        .andExpect(jsonPath("$.mergedIntoPropertyId").value(target));

    assertThat(storedStatus(source)).isEqualTo("MERGED");
    assertThat(auditCount(source, "MERGE", "APPLIED")).isEqualTo(1);
  }

  @Test
  void aStaleVersionIsRejectedAndLeavesThePropertyUnchanged() throws Exception {
    String admin = adminBearer("prop-admin-stale");
    String propertyId = createProperty(admin, "Stale Tower " + UUID.randomUUID());

    mockMvc
        .perform(
            post("/api/admin/properties/" + propertyId + "/activate")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":99}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PROPERTY_VERSION_CONFLICT"));

    assertThat(storedStatus(propertyId)).isEqualTo("DRAFT");
    assertThat(auditCount(propertyId, "ACTIVATE", "APPLIED")).isZero();
  }

  @Test
  void aMergedPropertyRefusesFurtherTransitions() throws Exception {
    String admin = adminBearer("prop-admin-terminal");
    String target = createProperty(admin, "Terminal Target " + UUID.randomUUID());
    String source = createProperty(admin, "Terminal Source " + UUID.randomUUID());

    mockMvc
        .perform(
            post("/api/admin/properties/" + source + "/merge")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"targetPropertyId\":\"" + target + "\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/admin/properties/" + source + "/activate")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PROPERTY_STATE_CONFLICT"));
  }

  @Test
  void anActionAgainstAnUnknownPropertyIs404AndStillAudited() throws Exception {
    String admin = adminBearer("prop-admin-missing");
    String missing = UUID.randomUUID().toString();

    mockMvc
        .perform(
            post("/api/admin/properties/" + missing + "/activate")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PROPERTY_NOT_FOUND"));

    assertThat(auditCount(missing, "ACTIVATE", "NOT_FOUND")).isEqualTo(1);
  }

  private static String bearer(String subject) {
    return "Bearer " + subject;
  }

  /** Provisions the account, then promotes it to ADMIN by SQL (the manual bootstrap path). */
  private String adminBearer(String subject) throws Exception {
    String bearer = bearer(subject);
    String body =
        mockMvc
            .perform(get("/api/me").header("Authorization", bearer))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String accountId = JsonPath.read(body, "$.accountId");
    jdbcTemplate.update(
        "update identity.account set role = 'ADMIN' where id = ?", UUID.fromString(accountId));
    return bearer;
  }

  private String createProperty(String bearer, String name) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/properties")
                    .header("Authorization", bearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"type\":\"BUILDING\",\"canonicalName\":\""
                            + name
                            + "\",\"allowDuplicate\":true}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.propertyId");
  }

  private String storedStatus(String propertyId) {
    return jdbcTemplate.queryForObject(
        "select status from properties.property where id = ?",
        String.class,
        UUID.fromString(propertyId));
  }

  private Integer auditCount(String propertyId, String action, String outcome) {
    return jdbcTemplate.queryForObject(
        "select count(*) from properties.property_admin_audit_event"
            + " where property_id = ? and action = ? and outcome = ?",
        Integer.class,
        UUID.fromString(propertyId),
        action,
        outcome);
  }
}
