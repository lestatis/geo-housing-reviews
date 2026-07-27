package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.ReviewId;

/** Raised when the author of a review attempts to signal that review as helpful. */
public final class SelfHelpfulSignalException extends RuntimeException {

  public SelfHelpfulSignalException(ReviewId reviewId) {
    super("the author cannot mark their own review as helpful: " + reviewId.value());
  }
}
