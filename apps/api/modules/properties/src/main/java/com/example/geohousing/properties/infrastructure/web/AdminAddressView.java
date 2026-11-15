package com.example.geohousing.properties.infrastructure.web;

/**
 * The full address as a moderator sees it, including the free text a resident typed.
 *
 * <p>Admin-only by construction: the public {@link PropertyResponse.AddressView} omits the free
 * text because it is the likeliest place an apartment number appears (DECISION_LOG P-018).
 */
public record AdminAddressView(
    String country,
    String city,
    String district,
    String street,
    String building,
    String originalText) {}
