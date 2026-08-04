package com.example.geohousing.app.metrics;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The health of the platform's queues, in one answer.
 *
 * <p>Nothing here names a person. The audit timeline answers "who did what", with a stated purpose
 * and a stated risk; this answers "is the work being done", which needs no names to be useful.
 *
 * @param oldestOpenCaseAgeDays null when nothing is open — zero would read as "waiting, but not
 *     long", which is the opposite of the truth
 * @param appealOverturnPercentage null when no appeal was heard, for the same reason: 0% claims
 *     nothing was overturned, when in fact nothing was decided. Always read beside the two counts
 *     it comes from, so "1 of 3" cannot be mistaken for a third of something large
 */
public record AdminMetrics(
    long openModerationCases,
    long reviewsAwaitingModeration,
    long pendingVerifications,
    @Schema(nullable = true) Long oldestOpenCaseAgeDays,
    long moderationDecisions,
    long appealsHeard,
    long appealsOverturned,
    @Schema(nullable = true) Long appealOverturnPercentage,
    long verificationsApproved,
    long verificationsRejected,
    long reviewsPublished,
    long reviewsRemoved) {}
