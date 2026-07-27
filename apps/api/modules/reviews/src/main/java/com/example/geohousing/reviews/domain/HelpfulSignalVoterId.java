package com.example.geohousing.reviews.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Opaque identity account identifier of a helpful-signal voter. It is deliberately distinct from
 * {@link AuthorId}: both reference identity accounts, but the roles have separate invariants.
 */
public record HelpfulSignalVoterId(UUID value) {

  public HelpfulSignalVoterId {
    Objects.requireNonNull(value, "helpful signal voter id value must not be null");
  }

  public static HelpfulSignalVoterId of(UUID value) {
    return new HelpfulSignalVoterId(value);
  }
}
