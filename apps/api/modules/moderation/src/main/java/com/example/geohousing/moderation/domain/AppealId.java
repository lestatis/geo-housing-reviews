package com.example.geohousing.moderation.domain;

import java.util.Objects;
import java.util.UUID;

/** Opaque, stable identifier for an {@link Appeal}. */
public record AppealId(UUID value) {

  public AppealId {
    Objects.requireNonNull(value, "appeal id value must not be null");
  }

  public static AppealId of(UUID value) {
    return new AppealId(value);
  }
}
