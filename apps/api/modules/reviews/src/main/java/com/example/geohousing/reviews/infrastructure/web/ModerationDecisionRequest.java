package com.example.geohousing.reviews.infrastructure.web;

/**
 * Body of a moderation decision. {@code version} is the review version the moderator saw; a
 * mismatch is a 409 so a moderator never acts on content that changed under them (API_GUIDELINES).
 * {@code reasonCode} is mandatory for every action (MODERATION.md) — the taxonomy arrives with the
 * moderation module, but an unexplained action is refused already.
 */
public record ModerationDecisionRequest(Long version, String reasonCode) {}
