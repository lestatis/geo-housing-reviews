package com.example.geohousing.properties.infrastructure.web;

import com.example.geohousing.properties.application.PropertyMatch;

/**
 * One search hit.
 *
 * <p>Carries just enough to render a result row and navigate to the property. The score is here so
 * a client can decide between "here it is" and "did you mean", not so anyone can reverse-engineer
 * the ranking — and there is no paid or sponsored concept in this module for it to be traded
 * against.
 */
public record PropertySearchHitResponse(
    String propertyId, String canonicalName, double score, Double distanceMeters) {

  static PropertySearchHitResponse from(PropertyMatch match) {
    return new PropertySearchHitResponse(
        match.propertyId().value().toString(),
        match.canonicalName(),
        match.score(),
        match.distanceMeters());
  }
}
