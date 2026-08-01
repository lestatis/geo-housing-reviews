package com.example.geohousing.moderation.infrastructure.web;

import java.util.List;

/** Appeals waiting to be heard, oldest first, each with the decision it challenges. */
public record AdminAppealQueueResponse(List<AdminAppealQueueEntryResponse> items) {}
