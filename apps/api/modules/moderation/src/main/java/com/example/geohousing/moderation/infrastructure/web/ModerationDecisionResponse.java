package com.example.geohousing.moderation.infrastructure.web;

import com.example.geohousing.moderation.domain.ModerationDecision;
import java.time.Instant;

/**
 * A decision as a moderator sees it — including the internal note, which is why this shape must
 * never be reused for anything a reporter or an author can read.
 */
public record ModerationDecisionResponse(
    String action,
    String reasonCode,
    int policyVersion,
    String publicExplanation,
    String internalNote,
    Long affectedTargetVersion,
    String decidedBy,
    Instant decidedAt) {

  static ModerationDecisionResponse from(ModerationDecision decision) {
    return new ModerationDecisionResponse(
        decision.action().name(),
        decision.reasonCode().value(),
        decision.policyVersion().value(),
        decision.publicExplanation().orElse(null),
        decision.internalNote().orElse(null),
        decision.affectedTargetVersion().orElse(null),
        decision.decidedBy().value().toString(),
        decision.decidedAt());
  }
}
