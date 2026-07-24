package com.example.geohousing.app.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
 * Drives the verification endpoints end to end through the real security chain (stub {@link
 * JwtDecoder}), including the privacy rules that keep a case invisible to anyone but its owner and
 * moderators, and the full open → approve loop that raises a review's tier.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(VerificationEndpointIntegrationTest.StubJwtDecoderConfig.class)
class VerificationEndpointIntegrationTest {

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
              .issuedAt(Instant.parse("2026-07-23T00:00:00Z"))
              .expiresAt(Instant.parse("2999-01-01T00:00:00Z"))
              .build();
    }
  }

  @Test
  void anonymousRequestsAreUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/verifications/" + UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(openBody(UUID.randomUUID().toString())))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/admin/verifications")).andExpect(status().isUnauthorized());
  }

  @Test
  void openingLeavesAPendingCaseVisibleToItsOwner() throws Exception {
    String owner = bearer("subject-open");
    String property = createProperty(owner);

    String body =
        mockMvc
            .perform(
                post("/api/verifications")
                    .header("Authorization", owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(openBody(property)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", Matchers.startsWith("/api/verifications/")))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.tier").value("UNVERIFIED"))
            .andExpect(jsonPath("$.badge").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // The moderator id is never exposed in the case view.
    assertThat(body).doesNotContain("decidedBy");
    String caseId = JsonPath.read(body, "$.caseId");
    mockMvc
        .perform(get("/api/verifications/" + caseId).header("Authorization", owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.caseId").value(caseId));
  }

  @Test
  void aCaseForAnUnknownPropertyIsNotFound() throws Exception {
    mockMvc
        .perform(
            post("/api/verifications")
                .header("Authorization", bearer("subject-noproperty"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(openBody(UUID.randomUUID().toString())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PROPERTY_NOT_FOUND"));
  }

  @Test
  void aSecondOpenPointsTheOwnerAtTheCaseTheyAlreadyHave() throws Exception {
    String owner = bearer("subject-dup");
    String property = createProperty(owner);
    String caseId = openCase(owner, property);

    mockMvc
        .perform(
            post("/api/verifications")
                .header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(openBody(property)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("VERIFICATION_CASE_ALREADY_EXISTS"))
        .andExpect(jsonPath("$.existingCaseId").value(caseId))
        .andExpect(header().string("Location", "/api/verifications/" + caseId));
  }

  @Test
  void aStrangerIsToldAPrivateCaseDoesNotExist() throws Exception {
    String owner = bearer("subject-owner-priv");
    String stranger = bearer("subject-stranger-priv");
    String property = createProperty(owner);
    String caseId = openCase(owner, property);

    mockMvc
        .perform(get("/api/verifications/" + caseId).header("Authorization", owner))
        .andExpect(status().isOk());
    // 404, not 403: a private workflow must not confirm that this account is verifying this
    // property.
    mockMvc
        .perform(get("/api/verifications/" + caseId).header("Authorization", stranger))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("VERIFICATION_CASE_NOT_FOUND"));
  }

  @Test
  void anOwnerCanCancelTheirPendingCase() throws Exception {
    String owner = bearer("subject-cancel");
    String property = createProperty(owner);
    String caseId = openCase(owner, property);

    mockMvc
        .perform(post("/api/verifications/" + caseId + "/cancel").header("Authorization", owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void theModeratorQueueIsAdminOnly() throws Exception {
    String user = bearer("subject-not-admin");
    accountIdOf(user);

    mockMvc
        .perform(get("/api/admin/verifications").header("Authorization", user))
        .andExpect(status().isForbidden());
  }

  @Test
  void approvingRaisesTheTierOnTheAccountsReviewAndIsAudited() throws Exception {
    String owner = bearer("subject-approve-owner");
    String admin = adminBearer("subject-approve-admin");
    String adminAccountId = accountIdOf(admin);
    String ownerAccountId = accountIdOf(owner);
    String property = createProperty(owner);
    // The owner has a published review of the property; approving should raise its tier.
    String reviewId = submitAndPublishReview(owner, property);
    String caseId = openCase(owner, property);

    mockMvc
        .perform(
            post("/api/admin/verifications/" + caseId + "/approve")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"reasonCode\":\"CODE_CONFIRMED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.tier").value("RELATIONSHIP_SIGNAL"))
        .andExpect(jsonPath("$.badge.type").value("RELATIONSHIP_SIGNAL_CONFIRMED"));

    // The review now shows the signal tier.
    mockMvc
        .perform(get("/api/reviews/" + reviewId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verificationTier").value("RELATIONSHIP_SIGNAL"));

    // The decision is audited with the moderator and reason.
    java.util.Map<String, Object> audit =
        jdbcTemplate.queryForMap(
            "select actor_account_id::text as actor, action, reason_code, outcome"
                + " from verification.verification_decision_audit_event where case_id = ?::uuid",
            caseId);
    assertThat(audit.get("actor")).isEqualTo(adminAccountId);
    assertThat(audit.get("action")).isEqualTo("APPROVE");
    assertThat(audit.get("reason_code")).isEqualTo("CODE_CONFIRMED");
    assertThat(audit.get("outcome")).isEqualTo("APPLIED");
    assertThat(ownerAccountId).isNotEqualTo(adminAccountId);
  }

  @Test
  void revokingAnApprovedBadgeTakesItOffTheReview() throws Exception {
    String owner = bearer("subject-revoke-owner");
    String admin = adminBearer("subject-revoke-admin");
    String property = createProperty(owner);
    String reviewId = submitAndPublishReview(owner, property);
    String caseId = openCase(owner, property);
    mockMvc
        .perform(
            post("/api/admin/verifications/" + caseId + "/approve")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"reasonCode\":\"INVITE_OK\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/admin/verifications/" + caseId + "/revoke")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1,\"reasonCode\":\"FORGED_INVITE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tier").value("UNVERIFIED"))
        .andExpect(jsonPath("$.badge").doesNotExist());

    // The review survives; it just no longer carries the badge.
    mockMvc
        .perform(get("/api/reviews/" + reviewId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verificationTier").value("UNVERIFIED"));
  }

  @Test
  void aStaleVersionIsA409() throws Exception {
    String owner = bearer("subject-stale-owner");
    String admin = adminBearer("subject-stale-admin");
    String property = createProperty(owner);
    String caseId = openCase(owner, property);

    mockMvc
        .perform(
            post("/api/admin/verifications/" + caseId + "/approve")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":9,\"reasonCode\":\"CLEAN\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("VERIFICATION_VERSION_CONFLICT"));
  }

  @Test
  void anApproveWithoutAReasonIsRejected() throws Exception {
    String owner = bearer("subject-noreason-owner");
    String admin = adminBearer("subject-noreason-admin");
    String property = createProperty(owner);
    String caseId = openCase(owner, property);

    mockMvc
        .perform(
            post("/api/admin/verifications/" + caseId + "/approve")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"reasonCode\":\"  \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void aDecisionAgainstAMissingCaseIs404ButStillAudited() throws Exception {
    String admin = adminBearer("subject-missing-admin");
    String missing = UUID.randomUUID().toString();

    mockMvc
        .perform(
            post("/api/admin/verifications/" + missing + "/reject")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"reasonCode\":\"NO_SUCH\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("VERIFICATION_CASE_NOT_FOUND"));

    Integer audited =
        jdbcTemplate.queryForObject(
            "select count(*) from verification.verification_decision_audit_event"
                + " where case_id = ?::uuid and outcome = 'NOT_FOUND'",
            Integer.class,
            missing);
    assertThat(audited).isEqualTo(1);
  }

  private static String bearer(String subject) {
    return "Bearer " + subject;
  }

  private String adminBearer(String subject) throws Exception {
    String token = bearer(subject);
    String accountId = accountIdOf(token);
    assertThat(
            jdbcTemplate.update(
                "update identity.account set role = 'ADMIN' where id = ?::uuid", accountId))
        .isEqualTo(1);
    return token;
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

  private String createProperty(String bearer) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/properties")
                    .header("Authorization", bearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"type\":\"BUILDING\",\"canonicalName\":\"Verify Tower "
                            + UUID.randomUUID()
                            + "\",\"allowDuplicate\":true}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.propertyId");
  }

  private String openCase(String bearer, String propertyId) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/verifications")
                    .header("Authorization", bearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(openBody(propertyId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.caseId");
  }

  private String submitAndPublishReview(String bearer, String propertyId) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/properties/" + propertyId + "/reviews")
                    .header("Authorization", bearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"relationshipType\":\"CURRENT_RESIDENT\",\"locale\":\"ka\","
                            + "\"body\":\"კარგი შენობა\",\"recommendation\":\"RECOMMEND\","
                            + "\"ratings\":[{\"category\":\"NOISE\",\"value\":4}]}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String reviewId = JsonPath.read(body, "$.reviewId");
    jdbcTemplate.update(
        "update reviews.review set status = 'PUBLISHED', published_at = now() where id = ?::uuid",
        reviewId);
    return reviewId;
  }

  private static String openBody(String propertyId) {
    return "{\"propertyId\":\""
        + propertyId
        + "\",\"relationshipClaim\":\"CURRENT_RESIDENT\",\"method\":\"BUILDING_CODE\"}";
  }
}
