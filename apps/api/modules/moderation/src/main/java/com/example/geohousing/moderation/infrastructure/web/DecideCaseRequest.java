package com.example.geohousing.moderation.infrastructure.web;

/**
 * A moderator's decision as sent. The decider is taken from the token, never the body: an audit
 * trail a caller can write someone else's name into is not an audit trail.
 */
public record DecideCaseRequest(
    String action, String reasonCode, String publicExplanation, String internalNote) {}
