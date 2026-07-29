package com.example.geohousing.app.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.geohousing.moderation.application.ModeratableTarget;
import com.example.geohousing.moderation.application.ModerationEffectApplier;
import com.example.geohousing.moderation.application.ModerationEffectConflictException;
import com.example.geohousing.moderation.application.ModerationTargetLookup;
import com.example.geohousing.moderation.domain.DecisionAction;
import com.example.geohousing.moderation.domain.ModerationTargetRef;
import com.example.geohousing.moderation.domain.ModeratorId;
import com.example.geohousing.moderation.domain.ReasonCode;
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
 * Proves the moderation module can actually reach the content it moderates, in a running context
 * with a real database — the first time the two modules are connected outside a unit test.
 *
 * <p>This exercises the moderation-side ports, which Spring resolves to the adapters over {@code
 * reviews.api}. The full report → case → decision flow cannot run yet: moderation has no
 * persistence adapters until chunk 5, so its repositories have no implementations to wire.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(ModerationReviewGatewayIntegrationTest.StubJwtDecoderConfig.class)
class ModerationReviewGatewayIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ModerationTargetLookup targetLookup;
  @Autowired private ModerationEffectApplier effectApplier;

  @TestConfiguration
  static class StubJwtDecoderConfig {

    @Bean
    JwtDecoder jwtDecoder() {
      return token ->
          Jwt.withTokenValue(token)
              .header("alg", "none")
              .subject(token)
              .issuedAt(Instant.parse("2026-07-29T00:00:00Z"))
              .expiresAt(Instant.parse("2999-01-01T00:00:00Z"))
              .build();
    }
  }

  @Test
  void moderationLearnsWhoWroteAReviewAndAtWhichVersion() throws Exception {
    String author = bearer("gateway-author");
    String propertyId = createProperty(author);
    String reviewId = publishedReview(author, propertyId);

    ModeratableTarget target =
        targetLookup.find(ModerationTargetRef.review(UUID.fromString(reviewId))).orElseThrow();

    assertThat(target.authorAccountId()).isEqualTo(UUID.fromString(accountIdOf(author)));
    assertThat(target.version()).isEqualTo(reviewVersion(reviewId));
  }

  @Test
  void aReviewThatDoesNotExistIsAbsentRatherThanAnError() {
    assertThat(targetLookup.find(ModerationTargetRef.review(UUID.randomUUID()))).isEmpty();
  }

  @Test
  void aRemoveDecisionActuallyTakesTheReviewOutOfThePublicListing() throws Exception {
    String author = bearer("gateway-remove-author");
    String propertyId = createProperty(author);
    String reviewId = publishedReview(author, propertyId);
    mockMvc
        .perform(get("/api/properties/" + propertyId + "/reviews"))
        .andExpect(jsonPath("$.items.length()").value(1));

    effectApplier.apply(
        ModerationTargetRef.review(UUID.fromString(reviewId)),
        DecisionAction.REMOVE,
        reviewVersion(reviewId),
        ModeratorId.of(UUID.randomUUID()),
        ReasonCode.of("DOXXING"));

    // The point of the whole chunk: a decision recorded in one module changes what readers of
    // another module's content actually see.
    mockMvc
        .perform(get("/api/properties/" + propertyId + "/reviews"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));
    mockMvc.perform(get("/api/reviews/" + reviewId)).andExpect(status().isNotFound());
  }

  @Test
  void theEffectIsAuditedByTheModuleThatOwnsTheContent() throws Exception {
    String author = bearer("gateway-audit-author");
    String reviewId = publishedReview(author, createProperty(author));

    effectApplier.apply(
        ModerationTargetRef.review(UUID.fromString(reviewId)),
        DecisionAction.HIDE,
        reviewVersion(reviewId),
        ModeratorId.of(UUID.randomUUID()),
        ReasonCode.of("PRIVACY_RISK"));

    // Reviews writes its own audit row atomically with the mutation, which is what makes
    // apply-then-record safe: if moderation's own record were lost, this trace would remain.
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from reviews.review_moderation_audit_event"
                    + " where review_id = ?::uuid and action = 'HIDE' and reason_code = ?",
                Integer.class,
                reviewId,
                "PRIVACY_RISK"))
        .isEqualTo(1);
  }

  @Test
  void contentThatMovedUnderTheModeratorIsRefused() throws Exception {
    String author = bearer("gateway-stale-author");
    String propertyId = createProperty(author);
    String reviewId = publishedReview(author, propertyId);

    assertThatThrownBy(
            () ->
                effectApplier.apply(
                    ModerationTargetRef.review(UUID.fromString(reviewId)),
                    DecisionAction.REMOVE,
                    reviewVersion(reviewId) + 3,
                    ModeratorId.of(UUID.randomUUID()),
                    ReasonCode.of("DOXXING")))
        .isInstanceOf(ModerationEffectConflictException.class);

    mockMvc
        .perform(get("/api/properties/" + propertyId + "/reviews"))
        .andExpect(jsonPath("$.items.length()").value(1));
  }

  private long reviewVersion(String reviewId) {
    Long version =
        jdbcTemplate.queryForObject(
            "select version from reviews.review where id = ?::uuid", Long.class, reviewId);
    return version == null ? 0L : version;
  }

  private static String bearer(String subject) {
    return "Bearer subject-" + subject;
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
                        "{\"type\":\"BUILDING\",\"canonicalName\":\"Gateway Tower "
                            + UUID.randomUUID()
                            + "\",\"allowDuplicate\":true}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.propertyId");
  }

  private String publishedReview(String bearer, String propertyId) throws Exception {
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

    String moderator = bearer("gateway-moderator");
    String moderatorAccount = accountIdOf(moderator);
    jdbcTemplate.update(
        "update identity.account set role = 'ADMIN' where id = ?::uuid", moderatorAccount);
    mockMvc
        .perform(
            post("/api/admin/reviews/" + reviewId + "/publish")
                .header("Authorization", moderator)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + reviewVersion(reviewId) + ",\"reasonCode\":\"CLEAN\"}"))
        .andExpect(status().isOk());
    return reviewId;
  }
}
