package com.example.geohousing.reviews.infrastructure.web;

import com.example.geohousing.reviews.domain.Review;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A review as a moderator sees it: the public view plus the author's account id.
 *
 * <p>Admin-only by construction — the public {@link ReviewResponse} carries no author identifier,
 * so a stable account id cannot be correlated across a reviewer's public history (DECISION_LOG
 * P-017).
 */
public record AdminReviewResponse(
    String reviewId,
    String propertyId,
    String authorAccountId,
    String relationshipType,
    LocalDate residenceFrom,
    LocalDate residenceTo,
    String status,
    String verificationTier,
    ReviewResponse.ContentView content,
    Instant publishedAt,
    Instant createdAt,
    Instant updatedAt,
    long version,
    long helpfulCount) {

  static AdminReviewResponse from(Review review, long helpfulCount) {
    ReviewResponse publicView = ReviewResponse.from(review, helpfulCount);
    return new AdminReviewResponse(
        publicView.reviewId(),
        publicView.propertyId(),
        review.authorId().value().toString(),
        publicView.relationshipType(),
        publicView.residenceFrom(),
        publicView.residenceTo(),
        publicView.status(),
        publicView.verificationTier(),
        publicView.content(),
        publicView.publishedAt(),
        publicView.createdAt(),
        publicView.updatedAt(),
        publicView.version(),
        publicView.helpfulCount());
  }
}
