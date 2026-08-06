package com.example.geohousing.app.audit;

import java.util.List;

/**
 * A page of the timeline, newest first.
 *
 * @param nextCursor pass back as {@code cursor} to continue; absent when nothing remains behind
 *     this page. Opaque by design — where a page ended is not part of the contract
 * @param appliedActorAccountId whose actions this page actually shows, or null for everyone's. A
 *     continuation inherits its filter from the cursor, so a caller that sent only a cursor never
 *     stated this — and a screen that guessed from the request would label one person's actions
 *     "everyone", which is the misreading this whole endpoint exists to prevent
 */
public record AuditTimelineResponse(
    List<AuditEntryView> items, String nextCursor, String appliedActorAccountId) {}
