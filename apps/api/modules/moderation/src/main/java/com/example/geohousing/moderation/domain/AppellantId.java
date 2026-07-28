package com.example.geohousing.moderation.domain;

import java.util.Objects;
import java.util.UUID;

/** The account appealing a decision made against it. */
public record AppellantId(UUID value) {

  public AppellantId {
    Objects.requireNonNull(value, "appellant id value must not be null");
  }

  public static AppellantId of(UUID value) {
    return new AppellantId(value);
  }
}
