package com.example.geohousing.identity.infrastructure.web;

/**
 * Body of {@code PATCH /api/me/profile}. {@code version} is the profile version the client last
 * saw; a mismatch yields a 409 so a concurrent edit is never silently overwritten. Field-level
 * validation rides on the domain types ({@code Pseudonym}, {@code PublicProfile}).
 */
public record UpdateProfileRequest(
    String pseudonym, String avatarUrl, String locale, long version) {}
