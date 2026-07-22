package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.CategoryRating;
import com.example.geohousing.reviews.domain.Recommendation;
import java.util.List;
import java.util.Objects;

/**
 * The written part of a review, as supplied by its author. Every submission and every edit carries
 * a complete content set — an edit replaces the content rather than patching it, which is what
 * makes each stored version independently readable during moderation.
 *
 * <p>{@code pros} and {@code cons} are optional.
 */
public record ReviewContent(
    String locale,
    String body,
    String pros,
    String cons,
    Recommendation recommendation,
    List<CategoryRating> ratings) {

  public ReviewContent {
    ratings = List.copyOf(Objects.requireNonNull(ratings, "ratings"));
  }
}
