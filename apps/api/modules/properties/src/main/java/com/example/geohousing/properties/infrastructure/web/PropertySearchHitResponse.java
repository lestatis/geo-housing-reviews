package com.example.geohousing.properties.infrastructure.web;

import com.example.geohousing.properties.application.PropertyMatch;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One search hit.
 *
 * <p>Carries just enough to render a result row and navigate to the property. The score is here so
 * a client can decide between "here it is" and "did you mean", not so anyone can reverse-engineer
 * the ranking — and there is no paid or sponsored concept in this module for it to be traded
 * against.
 *
 * <p>The address is a summary rather than the detail endpoint's {@link
 * PropertyResponse.AddressView}: {@code originalText} is free text a result row does not need, so
 * the hit exposes strictly less than the public detail endpoint already does.
 *
 * <p>{@code address} is null when the property has no address, and each part of it is null when
 * that part is not recorded; the backend serializes those as JSON {@code null}, so the contract
 * says so.
 */
public record PropertySearchHitResponse(
    String propertyId,
    String canonicalName,
    String type,
    @Schema(nullable = true) AddressSummaryView address,
    double score,
    Double distanceMeters) {

  /** The publicly safe address parts of a search hit; every part may be blank. */
  public record AddressSummaryView(
      @Schema(nullable = true) String city,
      @Schema(nullable = true) String district,
      @Schema(nullable = true) String street,
      @Schema(nullable = true) String building) {}

  static PropertySearchHitResponse from(PropertyMatch match) {
    return new PropertySearchHitResponse(
        match.propertyId().value().toString(),
        match.canonicalName(),
        match.type().name(),
        match
            .address()
            .map(a -> new AddressSummaryView(a.city(), a.district(), a.street(), a.building()))
            .orElse(null),
        match.score(),
        match.distanceMeters());
  }
}
