package com.example.geohousing.properties.domain;

/**
 * A structured address value object. Every part except the country is optional and the original
 * free-text form is preserved, because Georgian addresses do not all fit one rigid pattern (see
 * {@code docs/DOMAIN_MODEL.md}). The country is a 2-letter code, defaulting to {@code GE}.
 */
public record Address(
    String country,
    String city,
    String district,
    String street,
    String building,
    String originalText) {

  public Address {
    country = (country == null || country.isBlank()) ? "GE" : country.trim();
    if (country.length() != 2) {
      throw new IllegalArgumentException("country must be a 2-letter code");
    }
  }
}
