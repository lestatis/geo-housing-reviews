package com.example.geohousing.properties.infrastructure.web;

/**
 * Body of an admin lifecycle action. {@code version} is the property version the admin saw; a
 * mismatch is a 409 so a moderator never acts on stale content (API_GUIDELINES). {@code
 * targetPropertyId} applies to merge only.
 */
public record AdminLifecycleRequest(long version, String targetPropertyId) {}
