package com.example.geohousing.properties.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * The admin account performing a lifecycle action, held as an opaque identifier. Like {@link
 * CreatorId} it is an identity account id, but this module never depends on the identity module's
 * types or tables (ARCHITECTURE boundary rules). Named for its role rather than reusing {@code
 * CreatorId}, so an audit row can never be mistaken for authorship.
 */
public record AdminId(UUID value) {

  public AdminId {
    Objects.requireNonNull(value, "admin id value must not be null");
  }

  public static AdminId of(UUID value) {
    return new AdminId(value);
  }
}
