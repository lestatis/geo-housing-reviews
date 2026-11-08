package com.example.geohousing.properties.application;

/**
 * The publicly safe part of a matched property's address.
 *
 * <p>Deliberately less than the domain {@link com.example.geohousing.properties.domain.Address}:
 * {@code originalText} is user-entered free text and the likeliest place an apartment number is
 * hiding, and {@code country} is effectively constant. A result row needs only enough to tell two
 * similarly-named buildings apart, so the summary is strictly less than the public detail endpoint
 * already exposes.
 */
public record PropertyAddressSummary(
    String city, String district, String street, String building) {}
