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
import com.example.geohousing.verification.application.VerificationExpiryService;
import com.example.geohousing.verification.application.VerificationSubmissionService;
import com.example.geohousing.verification.domain.AccountRef;
import com.example.geohousing.verification.domain.ModeratorId;
import com.example.geohousing.verification.domain.RelationshipClaim;
import com.example.geohousing.verification.domain.VerificationCase;
import com.example.geohousing.verification.domain.VerificationMethod;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Revocation and expiry against real Postgres: both take a badge away and revert the account's
 * review to unverified, and neither deletes the review or the case (TRUST_VERIFICATION.md §8).
 */
@Testcontainers
@SpringBootTest
class VerificationRevocationIntegrationTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-23T10:00:00Z"), ZoneOffset.UTC);
  private static final ModeratorId MODERATOR = ModeratorId.of(UUID.randomUUID());

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Autowired private VerificationSubmissionService submission;
  @Autowired private VerificationDecisionService decisions;
  @Autowired private VerificationExpiryService expiry;
  @Autowired private com.example.geohousing.properties.application.PropertyRepository properties;
  @Autowired private com.example.geohousing.reviews.application.ReviewRepository reviews;

  @Autowired
  private com.example.geohousing.verification.application.VerificationCaseRepository cases;

  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID storeProperty() {
    PropertyId id = PropertyId.of(UUID.randomUUID());
    properties.create(
        Property.create(
            id,
            PropertyType.BUILDING,
            "Revoke Tower " + UUID.randomUUID(),
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
            RelationshipType.CURRENT_RESIDENT,
            null,
            CLOCK);
    review.appendVersion("ka", "body", null, null, Recommendation.NEUTRAL, List.of(), null, CLOCK);
    review.submit(CLOCK);
    reviews.create(review);
    return review;
  }

  private VerificationCase openCase(UUID account, UUID property) {
    return submission.open(
        new OpenVerificationCommand(
            AccountRef.of(account),
            com.example.geohousing.verification.domain.PropertyRef.of(property),
            RelationshipClaim.CURRENT_RESIDENT,
            VerificationMethod.INVITATION));
  }

  @Test
  void revokingTakesTheBadgeAwayAndLeavesTheReviewInPlace() {
    UUID property = storeProperty();
    UUID account = UUID.randomUUID();
    Review review = storeLiveReview(account, property);
    VerificationCase opened = openCase(account, property);
    decisions.approve(MODERATOR, opened.id(), opened.version(), "INVITE_OK", null);
    assertThat(reviews.findById(review.id()).orElseThrow().verificationTier())
        .isEqualTo(VerificationTier.RELATIONSHIP_SIGNAL);

    // Reload to get the stored version rather than assuming how much it advanced.
    long approvedVersion = cases.findById(opened.id()).orElseThrow().version();
    decisions.revoke(MODERATOR, opened.id(), approvedVersion, "FORGED_INVITE").orElseThrow();

    // The badge is gone from the review, but the review itself still exists.
    assertThat(reviews.findById(review.id())).isPresent();
    assertThat(reviews.findById(review.id()).orElseThrow().verificationTier())
        .isEqualTo(VerificationTier.UNVERIFIED);

    Map<String, Object> audit =
        jdbcTemplate.queryForMap(
            "select action, reason_code, actor_account_id::text as actor"
                + " from verification.verification_decision_audit_event"
                + " where case_id = ?::uuid and action = 'REVOKE'",
            opened.id().value());
    assertThat(audit.get("reason_code")).isEqualTo("FORGED_INVITE");
    assertThat(audit.get("actor")).isEqualTo(MODERATOR.value().toString());
  }

  @Test
  void aLapsedBadgeExpiresAndTheReviewFallsBackToUnverified() {
    UUID property = storeProperty();
    UUID account = UUID.randomUUID();
    Review review = storeLiveReview(account, property);
    VerificationCase opened = openCase(account, property);
    // Approved with an expiry already in the past.
    decisions.approve(
        MODERATOR, opened.id(), opened.version(), "INVITE_OK", CLOCK.instant().minusSeconds(60));
    assertThat(reviews.findById(review.id()).orElseThrow().verificationTier())
        .isEqualTo(VerificationTier.RELATIONSHIP_SIGNAL);

    assertThat(expiry.expireLapsed(50)).isEqualTo(1);

    assertThat(reviews.findById(review.id()).orElseThrow().verificationTier())
        .isEqualTo(VerificationTier.UNVERIFIED);

    // A system expiry is audited with no actor — the only action the schema allows that for.
    Map<String, Object> audit =
        jdbcTemplate.queryForMap(
            "select action, actor_account_id::text as actor, reason_code"
                + " from verification.verification_decision_audit_event"
                + " where case_id = ?::uuid and action = 'EXPIRE'",
            opened.id().value());
    assertThat(audit.get("actor")).isNull();
    assertThat(audit.get("reason_code")).isEqualTo("POLICY_EXPIRY");
  }

  @Test
  void aBadgeWithoutAnExpiryIsUntouchedBySweeps() {
    UUID property = storeProperty();
    UUID account = UUID.randomUUID();
    VerificationCase opened = openCase(account, property);
    decisions.approve(MODERATOR, opened.id(), opened.version(), "INVITE_OK", null);

    expiry.expireLapsed(50);

    assertThat(
            jdbcTemplate.queryForObject(
                "select status from verification.verification_case where id = ?::uuid",
                String.class,
                opened.id().value()))
        .isEqualTo("APPROVED");
  }
}
