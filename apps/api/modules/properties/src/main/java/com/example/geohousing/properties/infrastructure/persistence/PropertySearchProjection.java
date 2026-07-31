package com.example.geohousing.properties.infrastructure.persistence;

import java.util.UUID;

/** Spring Data projection for the native search query: the property, its score and its distance. */
interface PropertySearchProjection {

  UUID getId();

  String getCanonicalName();

  double getScore();

  /** Metres from the searched point, or null when the search carried no point. */
  Double getDistanceMeters();
}
