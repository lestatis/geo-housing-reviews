package com.example.geohousing.reviews.domain;

/**
 * Raised when a review cannot be found for a given identifier — or when the viewer is not allowed
 * to know that it exists. Unpublished reviews (drafts, reviews awaiting moderation, hidden and
 * removed ones) are reported as missing rather than forbidden, so a stranger cannot probe for the
 * existence of someone else's review.
 */
public class ReviewNotFoundException extends RuntimeException {

  public ReviewNotFoundException(ReviewId reviewId) {
    super("review not found: " + reviewId.value());
  }
}
