package com.example.geohousing.app.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.geohousing.properties.domain.CreatorId;
import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyId;
import com.example.geohousing.properties.domain.PropertyType;
import com.example.geohousing.reviews.domain.AuthorId;
import com.example.geohousing.reviews.domain.Recommendation;
import com.example.geohousing.reviews.domain.RelationshipType;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewId;
import com.example.geohousing.reviews.domain.VerificationTier;
import com.example.geohousing.verification.application.OpenVerificationCommand;
import com.example.geohousing.verification.application.VerificationDecisionService;
import com.example.geohousing.verification.application.VerificationSubmissionService;
import com.example.geohousing.verification.domain.ModeratorId;
import com.example.geohousing.verification.domain.RelationshipClaim;
import com.example.geohousing.verification.domain.VerificationCase;
import com.example.geohousing.verification.domain.VerificationMethod;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The whole verification → reviews projection, end to end through real Spring wiring and real
 * Postgres: a moderator approving a case raises the tier on that account's review. This is the
 * first chunk in which the verification services run in the app context (their persistence now
 * exists).
 */
@Testcontainers
@SpringBootTest
class VerificationToReviewProjectionIntegrationTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-23T10:00:00Z"), ZoneOffset.UTC);
  private static final ModeratorId MODERATOR = ModeratorId.of(UUID.randomUUID());

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Autowired private VerificationSubmissionService submission;
  @Autowired private VerificationDecisionService decisions;
  @Autowired private com.example.geohousing.properties.application.PropertyRepository properties;
  @Autowired private com.example.geohousing.reviews.application.ReviewRepository reviews;

  private UUID storeProperty() {
    PropertyId id = PropertyId.of(UUID.randomUUID());
    properties.create(
        Property.create(
            id,
            PropertyType.BUILDING,
            "Verify Tower " + UUID.randomUUID(),
            CreatorId.of(UUID.randomUUID()),
            CLOCK));
    return id.value();
  }

  private Review storeLiveReview(UUID account, UUID property) {
    Review review =
        Review.create(
            ReviewId.of(UUID.randomUUID()),
            com.example.geohousing.reviews.domain.PropertyRef.of(property),
            AuthorId.of(account),
            RelationshipType.FORMER_RESIDENT,
            null,
            CLOCK);
    review.appendVersion("ka", "body", null, null, Recommendation.NEUTRAL, List.of(), null, CLOCK);
    review.submit(CLOCK);
    reviews.create(review);
    return review;
  }

  @Test
  void approvingACaseRaisesTheTierOnTheAccountsReview() {
    UUID property = storeProperty();
    UUID account = UUID.randomUUID();
    Review review = storeLiveReview(account, property);
    assertThat(reviews.findById(review.id()).orElseThrow().verificationTier())
        .isEqualTo(VerificationTier.UNVERIFIED);

    VerificationCase opened =
        submission.open(
            new OpenVerificationCommand(
                com.example.geohousing.verification.domain.AccountRef.of(account),
                com.example.geohousing.verification.domain.PropertyRef.of(property),
                RelationshipClaim.FORMER_RESIDENT,
                VerificationMethod.BUILDING_CODE));

    decisions.approve(MODERATOR, opened.id(), opened.version(), "CODE_CONFIRMED", null);

    // The push travelled verification -> reviews.api -> the review row on Postgres.
    assertThat(reviews.findById(review.id()).orElseThrow().verificationTier())
        .isEqualTo(VerificationTier.RELATIONSHIP_SIGNAL);
  }

  @Test
  void approvingWithNoReviewYetIsHarmless() {
    UUID property = storeProperty();
    UUID account = UUID.randomUUID();

    VerificationCase opened =
        submission.open(
            new OpenVerificationCommand(
                com.example.geohousing.verification.domain.AccountRef.of(account),
                com.example.geohousing.verification.domain.PropertyRef.of(property),
                RelationshipClaim.OWNER,
                VerificationMethod.INVITATION));

    // No review exists for this account+property; the approval still succeeds (verify-first gap).
    assertThat(decisions.approve(MODERATOR, opened.id(), opened.version(), "INVITE_OK", null))
        .isPresent();
  }
}
