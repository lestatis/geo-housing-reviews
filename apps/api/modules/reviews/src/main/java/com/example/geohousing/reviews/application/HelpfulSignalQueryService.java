package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.ReviewId;
import com.example.geohousing.reviews.domain.ReviewNotFoundException;
import com.example.geohousing.reviews.domain.ReviewStatus;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/** Reads public-safe helpfulness totals without exposing individual voter records. */
public final class HelpfulSignalQueryService {

  private final ReviewRepository reviewRepository;
  private final HelpfulSignalRepository helpfulSignalRepository;

  public HelpfulSignalQueryService(
      ReviewRepository reviewRepository, HelpfulSignalRepository helpfulSignalRepository) {
    this.reviewRepository = Objects.requireNonNull(reviewRepository, "reviewRepository");
    this.helpfulSignalRepository =
        Objects.requireNonNull(helpfulSignalRepository, "helpfulSignalRepository");
  }

  /**
   * Returns the active helpful-signal total only when the review is publicly visible. Unpublished
   * targets are treated as missing so this read path cannot probe review state.
   */
  public HelpfulSignalCount countForPublishedReview(ReviewId reviewId) {
    Objects.requireNonNull(reviewId, "reviewId");
    reviewRepository
        .findById(reviewId)
        .filter(review -> review.status() == ReviewStatus.PUBLISHED)
        .orElseThrow(() -> new ReviewNotFoundException(reviewId));
    return new HelpfulSignalCount(reviewId, helpfulSignalRepository.countActive(reviewId));
  }

  /**
   * Active totals for reviews the caller has <em>already</em> been authorised to see, for rendering
   * them in one query instead of one per review.
   *
   * <p>This deliberately performs no visibility check of its own: it exists to decorate reviews a
   * caller is holding, and those were resolved through {@link ReviewQueryService}, which applies
   * the viewer rules. It must never be used to turn an unchecked identifier into information — for
   * that, {@link #countForPublishedReview} is the guarded entry point. Reviews with no signals are
   * absent from the result and read as zero.
   */
  public Map<ReviewId, Long> activeCountsForVisibleReviews(Collection<ReviewId> reviewIds) {
    Objects.requireNonNull(reviewIds, "reviewIds");
    return helpfulSignalRepository.countActive(reviewIds);
  }
}
