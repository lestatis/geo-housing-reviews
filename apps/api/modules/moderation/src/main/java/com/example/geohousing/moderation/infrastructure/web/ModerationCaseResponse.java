package com.example.geohousing.moderation.infrastructure.web;

import com.example.geohousing.moderation.application.ModerationCaseSummary;
import com.example.geohousing.moderation.domain.ModerationCase;
import java.time.Instant;

/**
 * A case as it appears in the queue.
 *
 * <p>Carries a concern count rather than the reporters. One account can raise at most one live
 * report per target, so the count already tells a moderator whether this is one complaint or twenty
 * — and deciding by who complained rather than by what the content says is exactly the failure mode
 * MODERATION.md's anti-capture rules exist to prevent.
 */
public record ModerationCaseResponse(
    String caseId,
    String targetType,
    String targetId,
    String trigger,
    String status,
    String riskLevel,
    String assignedModerator,
    int concernCount,
    Instant openedAt,
    Instant firstResponseAt) {

  static ModerationCaseResponse from(ModerationCaseSummary summary) {
    ModerationCase moderationCase = summary.moderationCase();
    return new ModerationCaseResponse(
        moderationCase.id().value().toString(),
        moderationCase.target().type().name(),
        moderationCase.target().id().toString(),
        moderationCase.trigger().name(),
        moderationCase.status().name(),
        moderationCase.riskLevel().name(),
        moderationCase.assignedModerator().map(m -> m.value().toString()).orElse(null),
        summary.concernCount(),
        moderationCase.openedAt(),
        moderationCase.firstResponseAt().orElse(null));
  }
}
