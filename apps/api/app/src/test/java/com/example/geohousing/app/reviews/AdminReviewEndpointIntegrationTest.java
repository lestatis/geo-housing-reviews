package com.example.geohousing.app.reviews;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Drives the moderation endpoints end to end: role gate, state transitions, optimistic version
 * check, and — most importantly — that every decision leaves its audit row, reason code included.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(AdminReviewEndpointIntegrationTest.StubJwtDecoderConfig.class)
class AdminReviewEndpointIntegrationTest {

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
              .issuedAt(Instant.parse("2026-07-22T00:00:00Z"))
              .expiresAt(Instant.parse("2999-01-01T00:00:00Z"))
              .build();
    }
  }

  @Test
  void moderationRequiresTheAdminRole() throws Exception {
    String user = bearer("subject-mere-mortal");
    accountIdOf(user); // provisions the (non-admin) account

    mockMvc
        .perform(
            post("/api/admin/reviews/" + UUID.randomUUID() + "/publish")
                .header("Authorization", user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(decision(0, "CLEAN")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(post("/api/admin/reviews/" + UUID.randomUUID() + "/publish"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void aModeratorSeesAPendingReviewThatThePublicEndpointDenies() throws Exception {
    String author = bearer("subject-mod-pending-author");
    String admin = adminBearer("subject-mod-pending-admin");
    String reviewId = submitReview(author, "მოდერაციის მოლოდინში");

    mockMvc
        .perform(get("/api/admin/reviews/" + reviewId).header("Authorization", admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING_MODERATION"));
    // The same admin on the public endpoint is a plain user and is told it does not exist.
    mockMvc
        .perform(get("/api/reviews/" + reviewId).header("Authorization", admin))
        .andExpect(status().isNotFound());
  }

  @Test
  void publishingMakesTheReviewPublicAndWritesTheAuditRow() throws Exception {
    String author = bearer("subject-publish-author");
    String admin = adminBearer("subject-publish-admin");
    String adminAccountId = accountIdOf(admin);
    String reviewId = submitReview(author, "გამოსაქვეყნებელი");

    mockMvc
        .perform(
            post("/api/admin/reviews/" + reviewId + "/publish")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(decision(0, "CLEAN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLISHED"))
        .andExpect(jsonPath("$.publishedAt").isNotEmpty());

    // Now on the public listing for everyone.
    mockMvc
        .perform(get("/api/reviews/" + reviewId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLISHED"));

    Map<String, Object> audit =
        jdbcTemplate.queryForMap(
            "select moderator_account_id::text as moderator, action, review_version_id::text as"
                + " version_id, reason_code, outcome"
                + " from reviews.review_moderation_audit_event where review_id = ?::uuid",
            reviewId);
    assertThat(audit.get("moderator")).isEqualTo(adminAccountId);
    assertThat(audit.get("action")).isEqualTo("PUBLISH");
    assertThat(audit.get("reason_code")).isEqualTo("CLEAN");
    assertThat(audit.get("outcome")).isEqualTo("APPLIED");
    // The audit names the exact content version the moderator judged.
    assertThat(audit.get("version_id"))
        .isEqualTo(
            jdbcTemplate.queryForObject(
                "select current_version_id::text from reviews.review where id = ?::uuid",
                String.class,
                reviewId));
  }

  @Test
  void rejectingIsTerminalAndTheAuthorMayStartFresh() throws Exception {
    String author = bearer("subject-reject-author");
    String admin = adminBearer("subject-reject-admin");
    String propertyId = createProperty(author, "Reject Tower " + UUID.randomUUID());
    String reviewId = submitReviewTo(author, propertyId, "უარსაყოფი");

    mockMvc
        .perform(
            post("/api/admin/reviews/" + reviewId + "/reject")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(decision(0, "PERSONAL_DATA")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"));

    // The slot is free: the same author can submit a fresh review of the same property.
    mockMvc
        .perform(
            post("/api/properties/" + propertyId + "/reviews")
                .header("Authorization", author)
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitBody("გასწორებული ვერსია")))
        .andExpect(status().isCreated());
  }

  @Test
  void hideWithdrawsFromPublicViewAndRestoreBringsBack() throws Exception {
    String author = bearer("subject-hide-author");
    String admin = adminBearer("subject-hide-admin");
    String reviewId = submitReview(author, "დასამალი");
    moderate(admin, reviewId, "publish", 0, "CLEAN");

    moderate(admin, reviewId, "hide", 1, "UNDER_DISPUTE");
    mockMvc.perform(get("/api/reviews/" + reviewId)).andExpect(status().isNotFound());

    moderate(admin, reviewId, "restore", 2, "DISPUTE_RESOLVED");
    mockMvc
        .perform(get("/api/reviews/" + reviewId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLISHED"));

    Integer auditRows =
        jdbcTemplate.queryForObject(
            "select count(*) from reviews.review_moderation_audit_event where review_id = ?::uuid",
            Integer.class,
            reviewId);
    assertThat(auditRows).isEqualTo(3);
  }

  @Test
  void aStaleVersionIsA409AndLeavesNoAuditRow() throws Exception {
    String author = bearer("subject-stale-author");
    String admin = adminBearer("subject-stale-admin");
    String reviewId = submitReview(author, "ძველი ვერსიით");

    mockMvc
        .perform(
            post("/api/admin/reviews/" + reviewId + "/publish")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(decision(7, "CLEAN")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REVIEW_VERSION_CONFLICT"));

    Integer auditRows =
        jdbcTemplate.queryForObject(
            "select count(*) from reviews.review_moderation_audit_event where review_id = ?::uuid",
            Integer.class,
            reviewId);
    assertThat(auditRows).isZero();
  }

  @Test
  void anIllegalTransitionIsAStateConflict() throws Exception {
    String author = bearer("subject-conflict-author");
    String admin = adminBearer("subject-conflict-admin");
    String reviewId = submitReview(author, "ორჯერ გამოქვეყნება");
    moderate(admin, reviewId, "publish", 0, "CLEAN");

    mockMvc
        .perform(
            post("/api/admin/reviews/" + reviewId + "/publish")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(decision(1, "CLEAN")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REVIEW_STATE_CONFLICT"));
  }

  @Test
  void anActionAgainstAMissingReviewIs404ButStillAudited() throws Exception {
    String admin = adminBearer("subject-missing-admin");
    String missingId = UUID.randomUUID().toString();

    mockMvc
        .perform(
            post("/api/admin/reviews/" + missingId + "/remove")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(decision(0, "SPAM_ACCOUNT")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));

    Map<String, Object> audit =
        jdbcTemplate.queryForMap(
            "select action, outcome, reason_code from reviews.review_moderation_audit_event"
                + " where review_id = ?::uuid",
            missingId);
    assertThat(audit.get("outcome")).isEqualTo("NOT_FOUND");
    assertThat(audit.get("action")).isEqualTo("REMOVE");
    assertThat(audit.get("reason_code")).isEqualTo("SPAM_ACCOUNT");
  }

  @Test
  void aDecisionWithoutAReasonCodeIsRefused() throws Exception {
    String author = bearer("subject-noreason-author");
    String admin = adminBearer("subject-noreason-admin");
    String reviewId = submitReview(author, "უმიზეზო");

    mockMvc
        .perform(
            post("/api/admin/reviews/" + reviewId + "/reject")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"reasonCode\":\"  \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

    mockMvc
        .perform(get("/api/admin/reviews/" + reviewId).header("Authorization", admin))
        .andExpect(jsonPath("$.status").value("PENDING_MODERATION"));
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

  private void moderate(String admin, String reviewId, String action, long version, String reason)
      throws Exception {
    mockMvc
        .perform(
            post("/api/admin/reviews/" + reviewId + "/" + action)
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(decision(version, reason)))
        .andExpect(status().isOk());
  }

  private String createProperty(String bearer, String canonicalName) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/properties")
                    .header("Authorization", bearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"type\":\"BUILDING\",\"canonicalName\":\""
                            + canonicalName
                            + "\",\"allowDuplicate\":true}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.propertyId");
  }

  private String submitReview(String authorBearer, String reviewBody) throws Exception {
    return submitReviewTo(
        authorBearer,
        createProperty(authorBearer, "Moderation Tower " + UUID.randomUUID()),
        reviewBody);
  }

  private String submitReviewTo(String authorBearer, String propertyId, String reviewBody)
      throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/properties/" + propertyId + "/reviews")
                    .header("Authorization", authorBearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(submitBody(reviewBody)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.reviewId");
  }

  private static String submitBody(String reviewBody) {
    return "{\"relationshipType\":\"CURRENT_RESIDENT\",\"locale\":\"ka\",\"body\":\""
        + reviewBody
        + "\",\"recommendation\":\"RECOMMEND\","
        + "\"ratings\":[{\"category\":\"NOISE\",\"value\":4}]}";
  }

  private static String decision(long version, String reasonCode) {
    return "{\"version\":" + version + ",\"reasonCode\":\"" + reasonCode + "\"}";
  }
}
