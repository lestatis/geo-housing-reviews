package com.example.geohousing.identity.infrastructure.web;

/**
 * Body of a role change. {@code version} is the account version the administrator saw; a mismatch
 * is a 409 so two administrators never overwrite each other (API_GUIDELINES).
 */
public record ChangeRoleRequest(String role, Long version) {}
