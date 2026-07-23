package com.example.geohousing.verification.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * The moderator deciding a verification case — an opaque reference into the identity module, kept
 * distinct from {@link AccountRef} so the account under review can never be mistaken for the person
 * judging it.
 */
public record ModeratorId(UUID value) {

  public ModeratorId {
    Objects.requireNonNull(value, "value");
  }

  public static ModeratorId of(UUID value) {
    return new ModeratorId(value);
  }
}
