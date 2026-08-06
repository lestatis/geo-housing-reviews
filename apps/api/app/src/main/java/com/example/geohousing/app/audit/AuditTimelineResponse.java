package com.example.geohousing.app.audit;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A page of the timeline, newest first.
 *
 * @param nextCursor pass back as {@code cursor} to continue; absent when nothing remains behind
 *     this page. Opaque by design — where a page ended is not part of the contract
 * @param applied the query that produced this page, which a continuation inherits from its cursor
 *     rather than restating
 */
public record AuditTimelineResponse(
    List<AuditEntryView> items, @Schema(nullable = true) String nextCursor, AppliedQuery applied) {}
