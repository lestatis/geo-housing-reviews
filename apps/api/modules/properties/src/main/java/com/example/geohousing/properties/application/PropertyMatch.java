package com.example.geohousing.properties.application;

import com.example.geohousing.properties.domain.PropertyId;
import com.example.geohousing.properties.domain.PropertyType;
import java.util.Objects;
import java.util.Optional;

/**
 * One search hit: which property, what it is called, what it is, where it is, how well it matched,
 * and how far away it is.
 *
 * <p>The score is exposed so a client can decide whether to show "did you mean" rather than a
 * confident answer. It is a relevance signal, not a ranking formula — nothing about paid or
 * sponsored status exists in this module, and PRD_MVP.md §6's rule that paid status must never
 * affect organic rank therefore holds structurally rather than by policy.
 *
 * <p>The type and the address summary are here so a result row can tell two similarly-named
 * buildings apart; both come from columns the search query already reads, so neither adds a query.
 */
public record PropertyMatch(
    PropertyId propertyId,
    String canonicalName,
    PropertyType type,
    PropertyAddressSummary addressSummary,
    double score,
    Double distanceMeters) {

  public PropertyMatch {
    Objects.requireNonNull(propertyId, "propertyId");
    Objects.requireNonNull(canonicalName, "canonicalName");
    Objects.requireNonNull(type, "type");
  }

  /** Metres from the searched point; absent when the search carried no point. */
  public Optional<Double> distance() {
    return Optional.ofNullable(distanceMeters);
  }

  /**
   * The address summary; absent when the property has no address, so a caller renders "no address"
   * rather than an address whose every part happens to be blank.
   */
  public Optional<PropertyAddressSummary> address() {
    return Optional.ofNullable(addressSummary);
  }
}
