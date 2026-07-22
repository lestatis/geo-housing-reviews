package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.PropertyRef;
import com.example.geohousing.reviews.domain.ReviewId;

/**
 * Raised when an author already has a live review of the property. One author holds one review slot
 * per property; changing their mind means editing that review, which appends a version and
 * preserves the trail — not writing a second one. The existing review's id is carried so the caller
 * can point the author at it.
 */
public class DuplicateReviewException extends RuntimeException {

  private final transient ReviewId existingReviewId;

  public DuplicateReviewException(PropertyRef propertyRef, ReviewId existingReviewId) {
    super("author already has a review of property " + propertyRef.value());
    this.existingReviewId = existingReviewId;
  }

  public ReviewId existingReviewId() {
    return existingReviewId;
  }
}
