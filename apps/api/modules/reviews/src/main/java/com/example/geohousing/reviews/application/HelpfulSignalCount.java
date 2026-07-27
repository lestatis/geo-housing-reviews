package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.ReviewId;
import java.util.Objects;

/** Public-safe aggregate projection of active helpful signals for one review. */
public record HelpfulSignalCount(ReviewId reviewId, long value) {

  public HelpfulSignalCount {
    Objects.requireNonNull(reviewId, "reviewId");
    if (value < 0) {
      throw new IllegalArgumentException("helpful signal count must not be negative");
    }
  }
}
