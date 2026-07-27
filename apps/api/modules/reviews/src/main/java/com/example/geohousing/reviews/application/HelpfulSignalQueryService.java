package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.ReviewId;
import com.example.geohousing.reviews.domain.ReviewNotFoundException;
import com.example.geohousing.reviews.domain.ReviewStatus;
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
}
