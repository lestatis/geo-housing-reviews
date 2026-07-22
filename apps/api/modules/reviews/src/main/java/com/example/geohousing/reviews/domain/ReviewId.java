package com.example.geohousing.reviews.domain;

import java.util.Objects;
import java.util.UUID;

/** Opaque, stable identifier for a {@link Review}. */
public record ReviewId(UUID value) {

  public ReviewId {
    Objects.requireNonNull(value, "review id value must not be null");
  }

  public static ReviewId of(UUID value) {
    return new ReviewId(value);
  }
}
