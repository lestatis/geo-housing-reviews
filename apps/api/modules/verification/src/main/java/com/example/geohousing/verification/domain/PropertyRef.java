package com.example.geohousing.verification.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * The property a verification case is about, held as an opaque identifier. The property belongs to
 * the properties module; whether it exists is checked through that module's public contract at the
 * application layer, never by reading its tables.
 */
public record PropertyRef(UUID value) {

  public PropertyRef {
    Objects.requireNonNull(value, "property reference must not be null");
  }

  public static PropertyRef of(UUID value) {
    return new PropertyRef(value);
  }
}
