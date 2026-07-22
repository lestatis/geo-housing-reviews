package com.example.geohousing.reviews.domain;

/** Raised when a review is asked to make a lifecycle transition its current status forbids. */
public class IllegalReviewStateTransitionException extends RuntimeException {

  public IllegalReviewStateTransitionException(String message) {
    super(message);
  }
}
