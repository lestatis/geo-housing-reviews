package com.example.geohousing.reviews.infrastructure.web;

import com.example.geohousing.reviews.domain.CategoryRating;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewVersion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Public view of a review.
 *
 * <p>Two deliberate omissions. The {@code editReason} is written for moderators, not readers, so it
 * stays out of the public representation — {@code versionNumber} already tells a reader the review
 * was edited. And {@code verificationTier} is reported as the tier it is, never as a bare
 * "verified" flag: it records how the author's relationship to the property was evidenced, not that
 * their statements are true (docs/TRUST_VERIFICATION.md).
 *
 * <p>{@code helpfulCount} is an aggregate and nothing more: who signalled, and when, are never
 * exposed, because voter identities and timing are what make targeting and campaign analysis
 * possible (plan 008). The reader learns how many people found the review helpful, not which.
 */
public record ReviewResponse(
    String reviewId,
    String propertyId,
    String authorAccountId,
    String relationshipType,
    LocalDate residenceFrom,
    LocalDate residenceTo,
    String status,
    String verificationTier,
    ContentView content,
    Instant publishedAt,
    Instant createdAt,
    Instant updatedAt,
    long version,
    long helpfulCount) {

  public record ContentView(
      int versionNumber,
      String locale,
      String body,
      String pros,
      String cons,
      String recommendation,
      List<RatingView> ratings,
      Instant createdAt) {}

  public record RatingView(
      String category, Integer value, boolean notApplicable, String note, int categorySetVersion) {}

  /**
   * Builds the view. The count is always supplied by the caller rather than defaulted, so a path
   * that forgets it fails to compile instead of quietly reporting a review as having no helpful
   * signals when it has some.
   */
  static ReviewResponse from(Review review, long helpfulCount) {
    return new ReviewResponse(
        review.id().value().toString(),
        review.propertyRef().value().toString(),
        review.authorId().value().toString(),
        review.relationshipType().name(),
        review.residencePeriod().map(period -> period.from()).orElse(null),
        review.residencePeriod().map(period -> period.to()).orElse(null),
        review.status().name(),
        review.verificationTier().name(),
        review.currentVersion().map(ReviewResponse::toContentView).orElse(null),
        review.publishedAt().orElse(null),
        review.createdAt(),
        review.updatedAt(),
        review.version(),
        helpfulCount);
  }

  private static ContentView toContentView(ReviewVersion version) {
    return new ContentView(
        version.versionNumber(),
        version.locale(),
        version.body(),
        version.pros(),
        version.cons(),
        version.recommendation().name(),
        version.ratings().stream().map(ReviewResponse::toRatingView).toList(),
        version.createdAt());
  }

  private static RatingView toRatingView(CategoryRating rating) {
    return new RatingView(
        rating.category(),
        rating.value(),
        rating.notApplicable(),
        rating.note(),
        rating.categorySetVersion());
  }
}
