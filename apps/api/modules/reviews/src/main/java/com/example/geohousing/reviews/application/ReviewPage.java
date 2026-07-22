package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.Review;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One page of reviews. {@code nextCursor} is absent on the last page — a client stops when it is
 * missing rather than when the page is short.
 */
public record ReviewPage(List<Review> reviews, ReviewCursor nextCursor) {

  public ReviewPage {
    reviews = List.copyOf(Objects.requireNonNull(reviews, "reviews"));
  }

  public static ReviewPage lastPage(List<Review> reviews) {
    return new ReviewPage(reviews, null);
  }

  public Optional<ReviewCursor> next() {
    return Optional.ofNullable(nextCursor);
  }
}
