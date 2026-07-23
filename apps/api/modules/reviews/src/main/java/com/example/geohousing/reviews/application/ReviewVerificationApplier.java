package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.api.ReviewVerificationTier;
import com.example.geohousing.reviews.api.ReviewVerificationUpdater;
import com.example.geohousing.reviews.domain.AuthorId;
import com.example.geohousing.reviews.domain.PropertyRef;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.VerificationTier;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Implements the inbound {@link ReviewVerificationUpdater} contract: applies a verification tier to
 * the author's live review of a property. The one-live-review rule means there is at most one
 * review to update.
 *
 * <p>Translating the api tier to the domain tier here is the point of the boundary — verification
 * speaks the published vocabulary, and reviews maps it to its own.
 */
public final class ReviewVerificationApplier implements ReviewVerificationUpdater {

  private final ReviewRepository reviewRepository;
  private final Clock clock;

  public ReviewVerificationApplier(ReviewRepository reviewRepository, Clock clock) {
    this.reviewRepository = Objects.requireNonNull(reviewRepository, "reviewRepository");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public boolean applyTier(UUID authorAccountId, UUID propertyId, ReviewVerificationTier tier) {
    Objects.requireNonNull(authorAccountId, "authorAccountId");
    Objects.requireNonNull(propertyId, "propertyId");
    Objects.requireNonNull(tier, "tier");

    return reviewRepository
        .findLiveByAuthorAndProperty(AuthorId.of(authorAccountId), PropertyRef.of(propertyId))
        .map(review -> apply(review, tier))
        .orElse(false);
  }

  private boolean apply(Review review, ReviewVerificationTier tier) {
    review.updateVerificationTier(toDomain(tier), clock);
    reviewRepository.save(review);
    return true;
  }

  private static VerificationTier toDomain(ReviewVerificationTier tier) {
    return switch (tier) {
      case UNVERIFIED -> VerificationTier.UNVERIFIED;
      case RELATIONSHIP_SIGNAL -> VerificationTier.RELATIONSHIP_SIGNAL;
      case DOCUMENT_VERIFIED -> VerificationTier.DOCUMENT_VERIFIED;
    };
  }
}
