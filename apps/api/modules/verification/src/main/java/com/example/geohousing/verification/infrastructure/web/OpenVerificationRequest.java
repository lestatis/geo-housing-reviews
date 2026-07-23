package com.example.geohousing.verification.infrastructure.web;

/**
 * Request to open a verification case. The account comes from the bearer token, never the body, so
 * a caller cannot open a case as somebody else.
 */
public record OpenVerificationRequest(String propertyId, String relationshipClaim, String method) {}
