package com.example.geohousing.reviews.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * The account performing a moderation action — an opaque reference into the identity module,
 * deliberately distinct from {@link AuthorId} so a moderator id cannot slip into a place expecting
 * an author.
 */
public record ModeratorId(UUID value) {

  public ModeratorId {
    Objects.requireNonNull(value, "value");
  }

  public static ModeratorId of(UUID value) {
    return new ModeratorId(value);
  }
}
