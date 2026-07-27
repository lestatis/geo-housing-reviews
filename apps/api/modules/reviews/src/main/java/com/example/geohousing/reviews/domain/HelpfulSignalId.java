package com.example.geohousing.reviews.domain;

import java.util.Objects;
import java.util.UUID;

/** Opaque identifier for a private helpfulness signal. */
public record HelpfulSignalId(UUID value) {

  public HelpfulSignalId {
    Objects.requireNonNull(value, "helpful signal id value must not be null");
  }

  public static HelpfulSignalId of(UUID value) {
    return new HelpfulSignalId(value);
  }
}
