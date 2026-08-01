package com.example.geohousing.moderation.infrastructure.web;

import com.example.geohousing.moderation.application.PendingAppeal;
import com.example.geohousing.moderation.domain.ModerationDecision;
import java.time.Instant;

/**
 * One appeal waiting to be heard, with the decision it challenges.
 *
 * <p>The moderator who hears an appeal is by rule not the one who made the decision, so they arrive
 * with no memory of it. Carrying the contested decision here is what lets them read both sides
 * before choosing; the appellant's text alone would be half an argument.
 *
 * <p>Admin-only by construction, and deliberately so: {@code internalNote} is a note moderators
 * write to each other. Nothing author-facing is built from this record — the appellant's own view
 * is {@code AppealResponse}, which carries neither the note nor either moderator's identity.
 */
public record AdminAppealQueueEntryResponse(
    String appealId,
    String appealText,
    String status,
    String originalDecider,
    Instant createdAt,
    ContestedDecision contestedDecision) {

  /** What was decided, as the moderator hearing the appeal needs to read it. */
  public record ContestedDecision(
      String caseId,
      String targetType,
      String targetId,
      String action,
      String reasonCode,
      String publicExplanation,
      String internalNote,
      Instant decidedAt) {}

  static AdminAppealQueueEntryResponse from(PendingAppeal pending) {
    ModerationDecision decision = pending.contestedDecision();
    return new AdminAppealQueueEntryResponse(
        pending.appeal().id().value().toString(),
        pending.appeal().appealText(),
        pending.appeal().status().name(),
        pending.appeal().originalDecider().value().toString(),
        pending.appeal().createdAt(),
        new ContestedDecision(
            decision.caseId().value().toString(),
            pending.target().type().name(),
            pending.target().id().toString(),
            decision.action().name(),
            decision.reasonCode().value(),
            decision.publicExplanation().orElse(null),
            decision.internalNote().orElse(null),
            decision.decidedAt()));
  }
}
