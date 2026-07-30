package com.example.geohousing.moderation.infrastructure.web;

import java.util.List;

/** The moderator queue, oldest case first. */
public record ModerationQueueResponse(List<ModerationCaseResponse> items) {}
