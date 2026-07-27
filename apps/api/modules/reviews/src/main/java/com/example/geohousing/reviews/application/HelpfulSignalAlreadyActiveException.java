package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.ReviewId;

/** Raised when a voter tries to add a second active helpful signal to the same review. */
public final class HelpfulSignalAlreadyActiveException extends RuntimeException {

  public HelpfulSignalAlreadyActiveException(ReviewId reviewId) {
    super("an active helpful signal already exists for review " + reviewId.value());
  }
}
