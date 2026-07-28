package com.example.geohousing.moderation.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * A reference to the content under moderation.
 *
 * <p>The id is opaque and carries no foreign key: the content belongs to another module, and this
 * module reaches it only through that module's published contract (ARCHITECTURE boundary rules).
 * The type travels with the id because an id alone cannot say which module to ask.
 */
public record ModerationTargetRef(ModerationTargetType type, UUID id) {

  public ModerationTargetRef {
    Objects.requireNonNull(type, "target type must not be null");
    Objects.requireNonNull(id, "target id must not be null");
  }

  public static ModerationTargetRef review(UUID reviewId) {
    return new ModerationTargetRef(ModerationTargetType.REVIEW, reviewId);
  }
}
