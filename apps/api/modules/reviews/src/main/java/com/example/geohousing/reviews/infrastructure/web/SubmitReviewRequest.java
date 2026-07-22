package com.example.geohousing.reviews.infrastructure.web;

import java.time.LocalDate;
import java.util.List;

/**
 * A review as submitted by its author. The property comes from the path and the author from the
 * bearer token — neither is accepted from the body, so a caller cannot submit a review as somebody
 * else or against a different property than the one they addressed.
 */
public record SubmitReviewRequest(
    String relationshipType,
    LocalDate residenceFrom,
    LocalDate residenceTo,
    String locale,
    String body,
    String pros,
    String cons,
    String recommendation,
    List<CategoryRatingRequest> ratings) {

  /** {@code value} is null exactly when {@code notApplicable} is true. */
  public record CategoryRatingRequest(
      String category, Integer value, Boolean notApplicable, String note) {}
}
