package com.example.geohousing.app.audit;

import java.util.List;

/**
 * A page of the timeline, newest first.
 *
 * @param nextCursor pass back as {@code cursor} to continue; absent when nothing remains behind
 *     this page. Opaque by design — where a page ended is not part of the contract
 */
public record AuditTimelineResponse(List<AuditEntryView> items, String nextCursor) {}
