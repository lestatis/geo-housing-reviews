package com.example.geohousing.app.reviews;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.geohousing.reviews.api.ReviewVerificationTier;
import com.example.geohousing.reviews.api.ReviewVerificationUpdater;
import com.example.geohousing.reviews.application.ReviewRepository;
import com.example.geohousing.reviews.domain.AuthorId;
import com.example.geohousing.reviews.domain.PropertyRef;
import com.example.geohousing.reviews.domain.Recommendation;
import com.example.geohousing.reviews.domain.RelationshipType;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewId;
import com.example.geohousing.reviews.domain.VerificationTier;
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
 * The reviews module's inbound verification contract, driven through the real Spring wiring and
 * real Postgres. This is the reviews half of the verification → reviews projection: chunk 4 wires
 * and proves it here; the verification half joins once verification has its own persistence.
 */
@Testcontainers
@SpringBootTest
class ReviewVerificationProjectionIntegrationTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-23T10:00:00Z"), ZoneOffset.UTC);

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Autowired private ReviewVerificationUpdater verificationUpdater;
  @Autowired private ReviewRepository reviews;

  private Review storeLiveReview(AuthorId author, PropertyRef property) {
    Review review =
        Review.create(
            ReviewId.of(UUID.randomUUID()),
            property,
            author,
            RelationshipType.CURRENT_RESIDENT,
            null,
            CLOCK);
    review.appendVersion("ka", "body", null, null, Recommendation.NEUTRAL, List.of(), null, CLOCK);
    review.submit(CLOCK);
    reviews.create(review);
    return review;
  }

  @Test
  void applyingATierPersistsItOnTheAuthorsLiveReview() {
    AuthorId author = AuthorId.of(UUID.randomUUID());
    PropertyRef property = PropertyRef.of(UUID.randomUUID());
    Review review = storeLiveReview(author, property);

    boolean updated =
        verificationUpdater.applyTier(
            author.value(), property.value(), ReviewVerificationTier.RELATIONSHIP_SIGNAL);

    assertThat(updated).isTrue();
    assertThat(reviews.findById(review.id()).orElseThrow().verificationTier())
        .isEqualTo(VerificationTier.RELATIONSHIP_SIGNAL);
  }

  @Test
  void aLaterRevocationLowersThePersistedTierBackToUnverified() {
    AuthorId author = AuthorId.of(UUID.randomUUID());
    PropertyRef property = PropertyRef.of(UUID.randomUUID());
    Review review = storeLiveReview(author, property);
    verificationUpdater.applyTier(
        author.value(), property.value(), ReviewVerificationTier.DOCUMENT_VERIFIED);

    verificationUpdater.applyTier(
        author.value(), property.value(), ReviewVerificationTier.UNVERIFIED);

    assertThat(reviews.findById(review.id()).orElseThrow().verificationTier())
        .isEqualTo(VerificationTier.UNVERIFIED);
  }

  @Test
  void anAuthorWithoutALiveReviewIsANoOp() {
    boolean updated =
        verificationUpdater.applyTier(
            UUID.randomUUID(), UUID.randomUUID(), ReviewVerificationTier.RELATIONSHIP_SIGNAL);

    assertThat(updated).isFalse();
  }
}
