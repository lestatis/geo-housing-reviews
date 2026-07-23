package com.example.geohousing.reviews.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.geohousing.reviews.api.ReviewVerificationTier;
import com.example.geohousing.reviews.domain.AuthorId;
import com.example.geohousing.reviews.domain.PropertyRef;
import com.example.geohousing.reviews.domain.Recommendation;
import com.example.geohousing.reviews.domain.RelationshipType;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewId;
import com.example.geohousing.reviews.domain.ReviewStatus;
import com.example.geohousing.reviews.domain.VerificationTier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewVerificationApplierTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-23T10:00:00Z"), ZoneOffset.UTC);

  private final InMemoryReviewRepository repository = new InMemoryReviewRepository();
  private final ReviewVerificationApplier applier =
      new ReviewVerificationApplier(repository, CLOCK);

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
    repository.create(review);
    return review;
  }

  @Test
  void projectsTheTierOntoTheAuthorsLiveReview() {
    AuthorId author = AuthorId.of(UUID.randomUUID());
    PropertyRef property = PropertyRef.of(UUID.randomUUID());
    Review review = storeLiveReview(author, property);

    boolean updated =
        applier.applyTier(
            author.value(), property.value(), ReviewVerificationTier.RELATIONSHIP_SIGNAL);

    assertThat(updated).isTrue();
    assertThat(repository.findById(review.id()).orElseThrow().verificationTier())
        .isEqualTo(VerificationTier.RELATIONSHIP_SIGNAL);
  }

  @Test
  void aLaterRevocationLowersTheTierBackToUnverified() {
    AuthorId author = AuthorId.of(UUID.randomUUID());
    PropertyRef property = PropertyRef.of(UUID.randomUUID());
    Review review = storeLiveReview(author, property);
    applier.applyTier(author.value(), property.value(), ReviewVerificationTier.DOCUMENT_VERIFIED);

    applier.applyTier(author.value(), property.value(), ReviewVerificationTier.UNVERIFIED);

    assertThat(repository.findById(review.id()).orElseThrow().verificationTier())
        .isEqualTo(VerificationTier.UNVERIFIED);
  }

  @Test
  void anAuthorWithoutALiveReviewIsANoOp() {
    boolean updated =
        applier.applyTier(
            UUID.randomUUID(), UUID.randomUUID(), ReviewVerificationTier.RELATIONSHIP_SIGNAL);

    assertThat(updated).isFalse();
    assertThat(repository.byId).isEmpty();
  }

  @Test
  void aTerminalReviewIsNotItsLiveReviewSoNothingIsTouched() {
    AuthorId author = AuthorId.of(UUID.randomUUID());
    PropertyRef property = PropertyRef.of(UUID.randomUUID());
    Review review = storeLiveReview(author, property);
    Review loaded = repository.findById(review.id()).orElseThrow();
    loaded.reject(CLOCK);
    repository.save(loaded);
    assertThat(repository.findById(review.id()).orElseThrow().status())
        .isEqualTo(ReviewStatus.REJECTED);

    boolean updated =
        applier.applyTier(
            author.value(), property.value(), ReviewVerificationTier.RELATIONSHIP_SIGNAL);

    assertThat(updated).isFalse();
  }
}
