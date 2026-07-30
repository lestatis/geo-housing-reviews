package com.example.geohousing.moderation.infrastructure.web;

/**
 * A report as a client sends it. The reporter is taken from the token, never from the body — a
 * caller must not be able to file a report in someone else's name.
 */
public record SubmitReportRequest(
    String targetType, String targetId, String category, String description) {}
