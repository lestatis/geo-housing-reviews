package com.example.geohousing.app.reviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Drives the review endpoints end to end through the real security chain (stub {@link JwtDecoder},
 * as in the other endpoint tests), including the visibility rules that decide what a stranger is
 * allowed to learn about someone else's review.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(ReviewEndpointIntegrationTest.StubJwtDecoderConfig.class)
class ReviewEndpointIntegrationTest {

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
  void anonymousVisitorsCanReadButNeverWrite() throws Exception {
    // Reading is public (DECISION_LOG P-010) …
    String bearer = bearer("subject-anon-read");
    String propertyId = createProperty(bearer, "Anon Read Tower " + UUID.randomUUID());
    String publishedId = submitReview(bearer, propertyId, "საჯაროდ წასაკითხი");
    publish(publishedId);

    mockMvc
        .perform(get("/api/properties/" + propertyId + "/reviews"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].reviewId").value(publishedId));
    mockMvc
        .perform(get("/api/reviews/" + publishedId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reviewId").value(publishedId));

    // … but only of published content: an unpublished review does not exist for a visitor.
    String pendingId =
        submitReview(
            bearer("subject-anon-hidden"),
            createProperty(bearer, "Anon Hidden Tower " + UUID.randomUUID()),
            "ჯერ მოდერაციაში");
    mockMvc.perform(get("/api/reviews/" + pendingId)).andExpect(status().isNotFound());

    // Writing always requires an account.
    mockMvc
        .perform(
            post("/api/properties/" + propertyId + "/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitBody("კარგი შენობა")))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            put("/api/reviews/" + publishedId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody("გადაწერა", "anon")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void submittingAReviewLeavesItAwaitingModerationRatherThanPublished() throws Exception {
    String bearer = bearer("subject-submit");
    String propertyId = createProperty(bearer, "Submit Tower " + UUID.randomUUID());

    String body =
        mockMvc
            .perform(
                post("/api/properties/" + propertyId + "/reviews")
                    .header("Authorization", bearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(submitBody("კარგი შენობა, მშვიდი მეზობლები")))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", Matchers.startsWith("/api/reviews/")))
            .andExpect(jsonPath("$.status").value("PENDING_MODERATION"))
            .andExpect(jsonPath("$.verificationTier").value("UNVERIFIED"))
            .andExpect(jsonPath("$.publishedAt").doesNotExist())
            .andExpect(jsonPath("$.content.versionNumber").value(1))
            .andExpect(jsonPath("$.content.body").value("კარგი შენობა, მშვიდი მეზობლები"))
            .andExpect(jsonPath("$.content.ratings[0].category").value("NOISE"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // The author is taken from the token, never from the body.
    assertThat(JsonPath.read(body, "$.authorAccountId").toString()).isEqualTo(accountIdOf(bearer));
    // The edit reason is written for moderators, so it is not part of the public representation.
    assertThat(body).doesNotContain("editReason");
  }

  @Test
  void aReviewOfAPropertyThatDoesNotExistIsNotFound() throws Exception {
    mockMvc
        .perform(
            post("/api/properties/" + UUID.randomUUID() + "/reviews")
                .header("Authorization", bearer("subject-nosuchproperty"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitBody("არარსებული")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PROPERTY_NOT_FOUND"));
  }

  @Test
  void aSecondSubmissionPointsTheAuthorAtTheReviewTheyAlreadyHave() throws Exception {
    String bearer = bearer("subject-duplicate");
    String propertyId = createProperty(bearer, "Duplicate Review Tower " + UUID.randomUUID());
    String reviewId = submitReview(bearer, propertyId, "პირველი მიმოხილვა");

    mockMvc
        .perform(
            post("/api/properties/" + propertyId + "/reviews")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitBody("მეორე მიმოხილვა")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"))
        .andExpect(jsonPath("$.existingReviewId").value(reviewId))
        .andExpect(header().string("Location", "/api/reviews/" + reviewId));
  }

  @Test
  void anAuthorSeesTheirOwnUnpublishedReviewButAStrangerIsToldItDoesNotExist() throws Exception {
    String author = bearer("subject-author");
    String stranger = bearer("subject-stranger");
    String propertyId = createProperty(author, "Visibility Tower " + UUID.randomUUID());
    String reviewId = submitReview(author, propertyId, "ჯერ არ გამოქვეყნებულა");

    mockMvc
        .perform(get("/api/reviews/" + reviewId).header("Authorization", author))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reviewId").value(reviewId));

    // 404, not 403: a "forbidden" would confirm that this author reviewed this property.
    mockMvc
        .perform(get("/api/reviews/" + reviewId).header("Authorization", stranger))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
  }

  @Test
  void anAdministratorGetsNoExtraVisibilityOnThePublicEndpoints() throws Exception {
    String author = bearer("subject-author-admin-check");
    String admin = bearer("subject-admin-viewer");
    String propertyId = createProperty(author, "Admin Visibility Tower " + UUID.randomUUID());
    String reviewId = submitReview(author, propertyId, "მოდერაციის მოლოდინში");
    promoteToAdmin(admin);

    // Elevated reading belongs to the moderation queue, where it is scoped and auditable.
    mockMvc
        .perform(get("/api/reviews/" + reviewId).header("Authorization", admin))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
  }

  @Test
  void anAuthorCanEditTheirReviewButAStrangerCannot() throws Exception {
    String author = bearer("subject-editor");
    String stranger = bearer("subject-not-editor");
    String propertyId = createProperty(author, "Edit Tower " + UUID.randomUUID());
    String reviewId = submitReview(author, propertyId, "თავდაპირველი ტექსტი");
    publish(reviewId);

    // While the review is published a stranger can already see it, so refusing their edit as 403
    // hides nothing. (Once the author's edit below sends it back to moderation it becomes
    // invisible again, and the same request would then be answered 404 — the visibility check
    // runs first, by design.)
    mockMvc
        .perform(
            put("/api/reviews/" + reviewId)
                .header("Authorization", stranger)
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody("გადაწერილი", "not mine")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("REVIEW_ACCESS_DENIED"));

    mockMvc
        .perform(
            put("/api/reviews/" + reviewId)
                .header("Authorization", author)
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody("დაზუსტებული ტექსტი", "დავაზუსტე დეტალები")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.versionNumber").value(2))
        .andExpect(jsonPath("$.content.body").value("დაზუსტებული ტექსტი"))
        // Editing a published review returns it to moderation before it is public again.
        .andExpect(jsonPath("$.status").value("PENDING_MODERATION"));

    // Now unpublished, the same stranger is no longer told the review exists at all.
    mockMvc
        .perform(
            put("/api/reviews/" + reviewId)
                .header("Authorization", stranger)
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody("გადაწერილი", "not mine")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
  }

  @Test
  void anEditWithoutAReasonIsRefused() throws Exception {
    String author = bearer("subject-noreason");
    String propertyId = createProperty(author, "No Reason Tower " + UUID.randomUUID());
    String reviewId = submitReview(author, propertyId, "ტექსტი");

    mockMvc
        .perform(
            put("/api/reviews/" + reviewId)
                .header("Authorization", author)
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody("ახალი ტექსტი", "   ")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void thePublicListingShowsPublishedReviewsAndPagesWithAnOpaqueCursor() throws Exception {
    String owner = bearer("subject-listing-owner");
    String propertyId = createProperty(owner, "Listing Tower " + UUID.randomUUID());

    String first = submitReview(bearer("subject-listing-1"), propertyId, "პირველი");
    String second = submitReview(bearer("subject-listing-2"), propertyId, "მეორე");
    String third = submitReview(bearer("subject-listing-3"), propertyId, "მესამე");
    // Published in order through the real moderation endpoint, so publication times strictly
    // increase and "newest first" means third, second, first.
    publish(first);
    publish(second);
    publish(third);
    // A fourth review stays in moderation and must not appear.
    submitReview(bearer("subject-listing-4"), propertyId, "მოდერაციაში");

    String page =
        mockMvc
            .perform(
                get("/api/properties/" + propertyId + "/reviews?limit=2")
                    .header("Authorization", owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].reviewId").value(third))
            .andExpect(jsonPath("$.items[1].reviewId").value(second))
            .andExpect(jsonPath("$.nextCursor").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String cursor = JsonPath.read(page, "$.nextCursor");
    mockMvc
        .perform(
            get("/api/properties/" + propertyId + "/reviews?limit=2&cursor=" + cursor)
                .header("Authorization", owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].reviewId").value(first))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
  }

  @Test
  void theOwnPendingReviewIsAbsentFromThePublicListingEvenForItsAuthor() throws Exception {
    String author = bearer("subject-own-pending");
    String propertyId = createProperty(author, "Own Pending Tower " + UUID.randomUUID());
    submitReview(author, propertyId, "ჩემი, ჯერ მოდერაციაში");

    mockMvc
        .perform(get("/api/properties/" + propertyId + "/reviews").header("Authorization", author))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
  }

  @Test
  void aCursorThisApiDidNotIssueIsRejectedRatherThanRestartingTheListing() throws Exception {
    String bearer = bearer("subject-bad-cursor");
    String propertyId = createProperty(bearer, "Cursor Tower " + UUID.randomUUID());

    mockMvc
        .perform(
            get("/api/properties/" + propertyId + "/reviews?cursor=not-a-real-cursor%21")
                .header("Authorization", bearer))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
  }

  @Test
  void aMalformedIdentifierIsABadRequestNotAServerError() throws Exception {
    String bearer = bearer("subject-malformed");

    mockMvc
        .perform(get("/api/reviews/not-a-uuid").header("Authorization", bearer))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void propertiesEndpointsStillUseTheirOwnErrorMapping() throws Exception {
    // The reviews advice is package-scoped; a missing property under /api/properties must still be
    // answered by the properties module's mapping.
    mockMvc
        .perform(
            get("/api/properties/" + UUID.randomUUID())
                .header("Authorization", bearer("subject-advice-scope")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PROPERTY_NOT_FOUND"));
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

  /**
   * Promotes the caller and proves it took effect by using an admin-only endpoint. Without that
   * check the admin-visibility test would pass even if the promotion silently updated nothing.
   */
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

  private String submitReview(String bearer, String propertyId, String reviewBody)
      throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/properties/" + propertyId + "/reviews")
                    .header("Authorization", bearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(submitBody(reviewBody)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.reviewId");
  }

  /**
   * Publishes through the real moderation endpoint (chunk 7), so these tests exercise the same
   * transition production uses. Every review here is published straight after submission, so the
   * expected version is always 0.
   */
  private void publish(String reviewId) throws Exception {
    String moderator = bearer("subject-resident-moderator");
    promoteToAdmin(moderator);
    mockMvc
        .perform(
            post("/api/admin/reviews/" + reviewId + "/publish")
                .header("Authorization", moderator)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"reasonCode\":\"CLEAN\"}"))
        .andExpect(status().isOk());
  }

  private static String submitBody(String reviewBody) {
    return "{\"relationshipType\":\"CURRENT_RESIDENT\","
        + "\"residenceFrom\":\"2023-03-01\","
        + "\"locale\":\"ka\","
        + "\"body\":\""
        + reviewBody
        + "\","
        + "\"pros\":\"მშვიდი ეზო\","
        + "\"recommendation\":\"RECOMMEND\","
        + "\"ratings\":[{\"category\":\"NOISE\",\"value\":4},"
        + "{\"category\":\"PARKING\",\"notApplicable\":true}]}";
  }

  private static String editBody(String reviewBody, String editReason) {
    return "{\"locale\":\"ka\",\"body\":\""
        + reviewBody
        + "\",\"recommendation\":\"NEUTRAL\","
        + "\"ratings\":[{\"category\":\"NOISE\",\"value\":3}],"
        + "\"editReason\":\""
        + editReason
        + "\"}";
  }
}
