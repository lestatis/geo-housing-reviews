package com.example.geohousing.properties.domain;

/**
 * A WGS84 geographic point. Validated to real lat/lng ranges; PostGIS geometry mapping is a later,
 * infrastructure-only concern (the domain stays framework- and library-free).
 */
public record Coordinates(double latitude, double longitude) {

  public Coordinates {
    if (latitude < -90 || latitude > 90) {
      throw new IllegalArgumentException("latitude must be between -90 and 90");
    }
    if (longitude < -180 || longitude > 180) {
      throw new IllegalArgumentException("longitude must be between -180 and 180");
    }
  }

  public static Coordinates of(double latitude, double longitude) {
    return new Coordinates(latitude, longitude);
  }
}
