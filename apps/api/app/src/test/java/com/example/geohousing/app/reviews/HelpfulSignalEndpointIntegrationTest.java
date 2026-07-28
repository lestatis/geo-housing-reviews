package com.example.geohousing.app.reviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
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
 * Drives the helpful-signal endpoints through the real security chain, covering the privacy rule
 * that matters most here: a reader learns <em>how many</em> accounts found a review helpful and
 * never <em>which</em> ones.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(HelpfulSignalEndpointIntegrationTest.StubJwtDecoderConfig.class)
class HelpfulSignalEndpointIntegrationTest {

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
              .issuedAt(Instant.parse("2026-07-27T00:00:00Z"))
              .expiresAt(Instant.parse("2999-01-01T00:00:00Z"))
              .build();
    }
  }

  @Test
  void signallingNeedsAnAccountButReadingTheCountDoesNot() throws Exception {
    String author = bearer("subject-hs-anon-author");
    String propertyId = createProperty(author, "Anon Helpful Tower " + UUID.randomUUID());
    String reviewId = publishedReview(author, propertyId);

    mockMvc
        .perform(post("/api/reviews/" + reviewId + "/helpful"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(delete("/api/reviews/" + reviewId + "/helpful"))
        .andExpect(status().isUnauthorized());

    // The aggregate itself is public (DECISION_LOG P-011): an anonymous reader sees the count.
    mockMvc
        .perform(get("/api/reviews/" + reviewId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.helpfulCount").value(0));
  }

  @Test
  void aSignalRaisesTheCountAndWithdrawingLowersItAgain() throws Exception {
    String author = bearer("subject-hs-author");
    String voter = bearer("subject-hs-voter");
    String propertyId = createProperty(author, "Helpful Tower " + UUID.randomUUID());
    String reviewId = publishedReview(author, propertyId);

    mockMvc
        .perform(post("/api/reviews/" + reviewId + "/helpful").header("Authorization", voter))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reviewId").value(reviewId))
        .andExpect(jsonPath("$.helpfulCount").value(1));

    mockMvc.perform(get("/api/reviews/" + reviewId)).andExpect(jsonPath("$.helpfulCount").value(1));

    mockMvc
        .perform(delete("/api/reviews/" + reviewId + "/helpful").header("Authorization", voter))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.helpfulCount").value(0));

    mockMvc.perform(get("/api/reviews/" + reviewId)).andExpect(jsonPath("$.helpfulCount").value(0));
  }

  @Test
  void theResponsesCarryTheAggregateAndNeverAVoterIdentity() throws Exception {
    String author = bearer("subject-hs-privacy-author");
    String voter = bearer("subject-hs-privacy-voter");
    String voterAccountId = accountIdOf(voter);
    String propertyId = createProperty(author, "Privacy Helpful Tower " + UUID.randomUUID());
    String reviewId = publishedReview(author, propertyId);

    String signalBody =
        mockMvc
            .perform(post("/api/reviews/" + reviewId + "/helpful").header("Authorization", voter))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String reviewBody =
        mockMvc
            .perform(get("/api/reviews/" + reviewId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String listBody =
        mockMvc
            .perform(get("/api/properties/" + propertyId + "/reviews"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // The voter is recorded in the database, but no public representation carries the identity —
    // nor a signal timestamp, which would let a campaign be reconstructed from the public API.
    assertThat(signalBody).doesNotContain(voterAccountId).doesNotContain("voter");
    assertThat(reviewBody).doesNotContain(voterAccountId).doesNotContain("voter");
    assertThat(listBody).doesNotContain(voterAccountId).doesNotContain("voter");
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from reviews.review_helpful_signal"
                    + " where review_id = ?::uuid and voter_account_id = ?::uuid"
                    + " and withdrawn_at is null",
                Integer.class,
                reviewId,
                voterAccountId))
        .isEqualTo(1);
  }

  @Test
  void anAuthorCannotSignalTheirOwnReview() throws Exception {
    String author = bearer("subject-hs-self");
    String propertyId = createProperty(author, "Self Helpful Tower " + UUID.randomUUID());
    String reviewId = publishedReview(author, propertyId);

    // Forbidden rather than hidden: the review is published, so the caller can already see it and
    // explaining the refusal discloses nothing.
    mockMvc
        .perform(post("/api/reviews/" + reviewId + "/helpful").header("Authorization", author))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("HELPFUL_SIGNAL_SELF_NOT_ALLOWED"));
    mockMvc
        .perform(delete("/api/reviews/" + reviewId + "/helpful").header("Authorization", author))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("HELPFUL_SIGNAL_SELF_NOT_ALLOWED"));

    mockMvc.perform(get("/api/reviews/" + reviewId)).andExpect(jsonPath("$.helpfulCount").value(0));
  }

  @Test
  void aSecondSignalFromTheSameAccountConflictsInsteadOfCountingTwice() throws Exception {
    String author = bearer("subject-hs-dup-author");
    String voter = bearer("subject-hs-dup-voter");
    String propertyId = createProperty(author, "Duplicate Helpful Tower " + UUID.randomUUID());
    String reviewId = publishedReview(author, propertyId);
    mockMvc
        .perform(post("/api/reviews/" + reviewId + "/helpful").header("Authorization", voter))
        .andExpect(status().isOk());

    mockMvc
        .perform(post("/api/reviews/" + reviewId + "/helpful").header("Authorization", voter))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("HELPFUL_SIGNAL_ALREADY_ACTIVE"));

    // The refused duplicate did not inflate the aggregate.
    mockMvc.perform(get("/api/reviews/" + reviewId)).andExpect(jsonPath("$.helpfulCount").value(1));
  }

  @Test
  void withdrawingWithoutAnActiveSignalIsHarmless() throws Exception {
    String author = bearer("subject-hs-idem-author");
    String voter = bearer("subject-hs-idem-voter");
    String propertyId = createProperty(author, "Idempotent Helpful Tower " + UUID.randomUUID());
    String reviewId = publishedReview(author, propertyId);

    // A retried DELETE must not be an error.
    mockMvc
        .perform(delete("/api/reviews/" + reviewId + "/helpful").header("Authorization", voter))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.helpfulCount").value(0));
  }

  @Test
  void anUnpublishedReviewCannotBeSignalledAndIsNotAcknowledgedEither() throws Exception {
    String author = bearer("subject-hs-unpublished-author");
    String voter = bearer("subject-hs-unpublished-voter");
    String propertyId = createProperty(author, "Unpublished Helpful Tower " + UUID.randomUUID());
    // Submitted but never published: still awaiting moderation.
    String reviewId = submitReview(author, propertyId);

    // Reported as missing rather than forbidden, so this path cannot be used to probe the
    // moderation queue for a review a stranger is not allowed to know exists.
    mockMvc
        .perform(post("/api/reviews/" + reviewId + "/helpful").header("Authorization", voter))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
    mockMvc
        .perform(delete("/api/reviews/" + reviewId + "/helpful").header("Authorization", voter))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
  }

  @Test
  void aReviewThatDoesNotExistIsNotFoundRatherThanAServerError() throws Exception {
    mockMvc
        .perform(
            post("/api/reviews/" + UUID.randomUUID() + "/helpful")
                .header("Authorization", bearer("subject-hs-missing")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
    mockMvc
        .perform(
            post("/api/reviews/not-a-uuid/helpful")
                .header("Authorization", bearer("subject-hs-malformed")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void theListingCarriesEachReviewsOwnCount() throws Exception {
    String owner = bearer("subject-hs-list-owner");
    String propertyId = createProperty(owner, "Listing Helpful Tower " + UUID.randomUUID());
    String quiet = publishedReview(bearer("subject-hs-list-b"), propertyId);
    String popular = publishedReview(bearer("subject-hs-list-a"), propertyId);

    for (String voter : List.of("subject-hs-v1", "subject-hs-v2", "subject-hs-v3")) {
      mockMvc
          .perform(
              post("/api/reviews/" + popular + "/helpful").header("Authorization", bearer(voter)))
          .andExpect(status().isOk());
    }

    // One page, two reviews, two different counts: a review with no signals reads as zero rather
    // than borrowing its neighbour's total or vanishing from the batch lookup.
    mockMvc
        .perform(get("/api/properties/" + propertyId + "/reviews"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].reviewId").value(popular))
        .andExpect(jsonPath("$.items[0].helpfulCount").value(3))
        .andExpect(jsonPath("$.items[1].reviewId").value(quiet))
        .andExpect(jsonPath("$.items[1].helpfulCount").value(0));
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

  private void promoteToAdmin(String bearer) throws Exception {
    String accountId = accountIdOf(bearer);
    int updated =
        jdbcTemplate.update(
            "update identity.account set role = 'ADMIN' where id = ?::uuid", accountId);
    assertThat(updated).isEqualTo(1);

    // An admin-only endpoint answering 200 proves the role is really in force for this caller.
    mockMvc
        .perform(get("/api/admin/accounts/" + accountId).header("Authorization", bearer))
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

  private String submitReview(String bearer, String propertyId) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/properties/" + propertyId + "/reviews")
                    .header("Authorization", bearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"relationshipType\":\"CURRENT_RESIDENT\",\"locale\":\"ka\","
                            + "\"body\":\"კარგი შენობა, მშვიდი მეზობლები\","
                            + "\"recommendation\":\"RECOMMEND\","
                            + "\"ratings\":[{\"category\":\"NOISE\",\"value\":4}]}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.reviewId");
  }

  /** Publishes through the real moderation endpoint, as the other endpoint tests do. */
  private String publishedReview(String bearer, String propertyId) throws Exception {
    String reviewId = submitReview(bearer, propertyId);
    String moderator = bearer("subject-hs-moderator");
    promoteToAdmin(moderator);
    mockMvc
        .perform(
            post("/api/admin/reviews/" + reviewId + "/publish")
                .header("Authorization", moderator)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"reasonCode\":\"CLEAN\"}"))
        .andExpect(status().isOk());
    return reviewId;
  }
}
