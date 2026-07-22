package com.example.geohousing.reviews.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * One immutable content version of a review (see {@code docs/DOMAIN_MODEL.md} ReviewVersion). An
 * edit never mutates a version — the {@link Review} aggregate appends a new one, preserving the
 * edit trail for moderation. Version numbers are assigned by the aggregate and are sequential per
 * review.
 */
public record ReviewVersion(
    UUID id,
    int versionNumber,
    String locale,
    String body,
    String pros,
    String cons,
    Recommendation recommendation,
    List<CategoryRating> ratings,
    String editReason,
    Instant createdAt) {

  public ReviewVersion {
    Objects.requireNonNull(id, "id");
    if (versionNumber < 1) {
      throw new IllegalArgumentException("versionNumber must be positive");
    }
    locale = requireText(locale, "locale");
    body = requireText(body, "body");
    Objects.requireNonNull(recommendation, "recommendation");
    ratings = List.copyOf(Objects.requireNonNull(ratings, "ratings"));
    Set<String> categories = new HashSet<>();
    for (CategoryRating rating : ratings) {
      if (!categories.add(rating.category())) {
        throw new IllegalArgumentException("duplicate category rating: " + rating.category());
      }
    }
    Objects.requireNonNull(createdAt, "createdAt");
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
