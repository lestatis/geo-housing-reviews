package com.example.geohousing.app.audit;

import java.util.List;

/** A page of the timeline, newest first. */
public record AuditTimelineResponse(List<AuditEntryView> items) {}
