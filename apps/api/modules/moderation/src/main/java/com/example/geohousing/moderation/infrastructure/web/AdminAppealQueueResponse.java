package com.example.geohousing.moderation.infrastructure.web;

import java.util.List;

/** Appeals waiting to be heard, oldest first. */
public record AdminAppealQueueResponse(List<AdminAppealResponse> items) {}
