package com.example.geohousing.properties.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * The account that created a property, held as an opaque identifier. It is an identity account id,
 * but this module never depends on the identity module's types or tables (ARCHITECTURE boundary
 * rules), so it is modelled locally as a bare UUID wrapper.
 */
public record CreatorId(UUID value) {

  public CreatorId {
    Objects.requireNonNull(value, "creator id value must not be null");
  }

  public static CreatorId of(UUID value) {
    return new CreatorId(value);
  }
}
