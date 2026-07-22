package com.example.geohousing.properties.infrastructure.web;

/**
 * Body of {@code POST /api/properties}. Address parts and coordinates are optional. {@code
 * allowDuplicate} is how a client confirms "yes, create it anyway" after being shown duplicate
 * candidates on a first attempt.
 */
public record CreatePropertyRequest(
    String type,
    String canonicalName,
    AddressPayload address,
    Double latitude,
    Double longitude,
    boolean allowDuplicate) {

  /** Structured address parts; every field is optional (Georgian addresses vary). */
  public record AddressPayload(
      String country,
      String city,
      String district,
      String street,
      String building,
      String originalText) {}
}
