package com.example.geohousing.identity.infrastructure.web;

import java.time.Instant;

/**
 * Body of a restriction. {@code reason} is required and is what the affected account can be told —
 * a restriction nobody can be given a reason for is one they cannot appeal or correct.
 *
 * <p>{@code endAt} may be omitted, meaning indefinite. That is a deliberate choice rather than a
 * missing field, so it is not defaulted to some arbitrary window.
 */
public record RestrictAccountRequest(String scope, String reason, Instant endAt) {}
