package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewId;
import com.example.geohousing.reviews.domain.ReviewNotFoundException;
import com.example.geohousing.reviews.domain.ReviewStatus;
import java.util.Optional;

/**
 * Who may see a single review. One rule, used by every path that resolves a review by id, so an
 * endpoint cannot accidentally skip the check.
 *
 * <p>Only {@code PUBLISHED} reviews are public. Everything else — a draft, one awaiting moderation,
 * one hidden or rejected — is visible to its author and to moderators, and reported as <em>not
 * found</em> to anyone else: a 403 would confirm that a particular person reviewed a particular
 * building, which is exactly what a hidden review should not reveal.
 */
final class ReviewVisibility {

  private ReviewVisibility() {}

  static Review requireVisible(Optional<Review> review, ReviewId reviewId, ReviewViewer viewer) {
    return review
        .filter(found -> isVisibleTo(found, viewer))
        .orElseThrow(() -> new ReviewNotFoundException(reviewId));
  }

  static boolean isVisibleTo(Review review, ReviewViewer viewer) {
    if (review.status() == ReviewStatus.PUBLISHED) {
      return true;
    }
    return viewer.moderator() || viewer.is(review.authorId());
  }
}
