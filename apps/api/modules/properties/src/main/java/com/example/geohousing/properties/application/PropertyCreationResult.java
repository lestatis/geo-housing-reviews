package com.example.geohousing.properties.application;

import com.example.geohousing.properties.domain.Property;
import java.util.List;

/**
 * Outcome of a create attempt: either the property was created, or the attempt was stopped because
 * possible duplicates were found (and the caller had not opted to proceed anyway). Duplicates are
 * an expected outcome of catalogue growth, not an error, so they are modelled as a result rather
 * than an exception.
 */
public sealed interface PropertyCreationResult {

  record Created(Property property) implements PropertyCreationResult {}

  record DuplicatesFound(List<DuplicateCandidate> candidates) implements PropertyCreationResult {}
}
