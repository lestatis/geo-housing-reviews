package com.example.geohousing.moderation.domain;

import java.util.Objects;
import java.util.UUID;

/** Opaque, stable identifier for a {@link ModerationDecision}. */
public record ModerationDecisionId(UUID value) {

  public ModerationDecisionId {
    Objects.requireNonNull(value, "moderation decision id value must not be null");
  }

  public static ModerationDecisionId of(UUID value) {
    return new ModerationDecisionId(value);
  }
}
