package com.example.geohousing.moderation.infrastructure.web;

/**
 * An appeal as its author sends it. They name the content, not a decision: an author knows their
 * review was taken down and should not need an internal identifier to say so.
 */
public record SubmitAppealRequest(String targetType, String targetId, String appealText) {}
