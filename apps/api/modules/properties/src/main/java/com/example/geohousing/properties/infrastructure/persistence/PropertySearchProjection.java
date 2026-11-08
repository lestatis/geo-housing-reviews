package com.example.geohousing.properties.infrastructure.persistence;

import java.util.UUID;

/**
 * Spring Data projection for the native search query: the property, its score and its distance,
 * plus the type and address columns the same query already reads.
 */
interface PropertySearchProjection {

  UUID getId();

  String getCanonicalName();

  String getType();

  /**
   * The address columns, all null when the property has no address row (the join is a LEFT JOIN).
   */
  String getCity();

  String getDistrict();

  String getStreet();

  String getBuilding();

  double getScore();

  /** Metres from the searched point, or null when the search carried no point. */
  Double getDistanceMeters();
}
