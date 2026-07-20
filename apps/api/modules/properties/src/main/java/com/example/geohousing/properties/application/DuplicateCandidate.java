package com.example.geohousing.properties.application;

import com.example.geohousing.properties.domain.PropertyId;
import java.util.Objects;

/**
 * A lightweight summary of an existing property that may be the same place as one a user is about
 * to create. The deterministic detection that produces candidates lands in the duplicate/geo chunk;
 * this is the shape the creation flow surfaces so the user can pick an existing property instead.
 */
public record DuplicateCandidate(PropertyId propertyId, String canonicalName) {

  public DuplicateCandidate {
    Objects.requireNonNull(propertyId, "propertyId");
    Objects.requireNonNull(canonicalName, "canonicalName");
  }
}
