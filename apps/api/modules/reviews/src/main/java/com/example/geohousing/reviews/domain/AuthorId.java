package com.example.geohousing.reviews.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * The account that wrote a review, held as an opaque identifier. It is an identity account id, but
 * this module never depends on the identity module's types or tables (ARCHITECTURE boundary rules).
 */
public record AuthorId(UUID value) {

  public AuthorId {
    Objects.requireNonNull(value, "author id value must not be null");
  }

  public static AuthorId of(UUID value) {
    return new AuthorId(value);
  }
}
