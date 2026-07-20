package com.example.geohousing.properties.domain;

import java.util.Objects;
import java.util.UUID;

/** Opaque, stable identifier for a {@link Property}. */
public record PropertyId(UUID value) {

  public PropertyId {
    Objects.requireNonNull(value, "property id value must not be null");
  }

  public static PropertyId of(UUID value) {
    return new PropertyId(value);
  }
}
