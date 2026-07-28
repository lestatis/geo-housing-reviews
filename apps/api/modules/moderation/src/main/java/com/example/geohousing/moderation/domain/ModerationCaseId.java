package com.example.geohousing.moderation.domain;

import java.util.Objects;
import java.util.UUID;

/** Opaque, stable identifier for a {@link ModerationCase}. */
public record ModerationCaseId(UUID value) {

  public ModerationCaseId {
    Objects.requireNonNull(value, "moderation case id value must not be null");
  }

  public static ModerationCaseId of(UUID value) {
    return new ModerationCaseId(value);
  }
}
