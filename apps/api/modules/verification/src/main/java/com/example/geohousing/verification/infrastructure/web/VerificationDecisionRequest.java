package com.example.geohousing.verification.infrastructure.web;

import java.time.Instant;

/**
 * Body of a moderation decision. {@code version} is the case version the moderator saw; a mismatch
 * is a 409 so a moderator never acts on a case that changed under them (API_GUIDELINES). {@code
 * reasonCode} is mandatory for every decision (TRUST_VERIFICATION.md §7). {@code validThrough}
 * applies to approval only — an optional expiry for the badge.
 */
public record VerificationDecisionRequest(Long version, String reasonCode, Instant validThrough) {}
