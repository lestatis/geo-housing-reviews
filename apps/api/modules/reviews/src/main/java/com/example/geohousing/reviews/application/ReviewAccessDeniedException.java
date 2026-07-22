package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.ReviewId;

/**
 * Raised when the caller may see a review but may not change it — editing someone else's published
 * review, for instance. Reviews the caller is not even allowed to know about are reported as not
 * found instead, so this never confirms the existence of hidden content.
 */
public class ReviewAccessDeniedException extends RuntimeException {

  public ReviewAccessDeniedException(ReviewId reviewId) {
    super("not allowed to modify review " + reviewId.value());
  }
}
