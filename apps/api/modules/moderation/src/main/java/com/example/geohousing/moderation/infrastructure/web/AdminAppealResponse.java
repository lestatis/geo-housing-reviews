package com.example.geohousing.moderation.infrastructure.web;

import com.example.geohousing.moderation.domain.Appeal;
import java.time.Instant;

/**
 * An appeal as a moderator working the queue sees it.
 *
 * <p>Names the moderator being appealed against, because whoever picks this up needs to know it is
 * not them. Admin-only by construction — the appellant's view carries neither identity.
 */
public record AdminAppealResponse(
    String appealId,
    String appealText,
    String status,
    String outcomeExplanation,
    String originalDecider,
    String decidedBy,
    Instant createdAt,
    Instant decidedAt) {

  static AdminAppealResponse from(Appeal appeal) {
    return new AdminAppealResponse(
        appeal.id().value().toString(),
        appeal.appealText(),
        appeal.status().name(),
        appeal.outcomeExplanation().orElse(null),
        appeal.originalDecider().value().toString(),
        appeal.decidedBy().map(m -> m.value().toString()).orElse(null),
        appeal.createdAt(),
        appeal.decidedAt().orElse(null));
  }
}
