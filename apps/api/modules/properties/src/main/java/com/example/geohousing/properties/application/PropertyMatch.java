package com.example.geohousing.properties.application;

import com.example.geohousing.properties.domain.PropertyId;
import java.util.Objects;
import java.util.Optional;

/**
 * One search hit: which property, how well it matched, and how far away it is.
 *
 * <p>The score is exposed so a client can decide whether to show "did you mean" rather than a
 * confident answer. It is a relevance signal, not a ranking formula — nothing about paid or
 * sponsored status exists in this module, and PRD_MVP.md §6's rule that paid status must never
 * affect organic rank therefore holds structurally rather than by policy.
 */
public record PropertyMatch(
    PropertyId propertyId, String canonicalName, double score, Double distanceMeters) {

  public PropertyMatch {
    Objects.requireNonNull(propertyId, "propertyId");
    Objects.requireNonNull(canonicalName, "canonicalName");
  }

  /** Metres from the searched point; absent when the search carried no point. */
  public Optional<Double> distance() {
    return Optional.ofNullable(distanceMeters);
  }
}
