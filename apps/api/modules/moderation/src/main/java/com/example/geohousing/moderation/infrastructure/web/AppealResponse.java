package com.example.geohousing.moderation.infrastructure.web;

import com.example.geohousing.moderation.domain.Appeal;
import java.time.Instant;

/**
 * An appeal as its appellant sees it.
 *
 * <p>Carries the outcome and the explanation owed to them, and neither moderator's identity — not
 * the one appealed against, nor the one who heard it. Naming them would turn a due-process record
 * into a target list.
 */
public record AppealResponse(
    String appealId,
    String status,
    String outcomeExplanation,
    Instant createdAt,
    Instant decidedAt) {

  static AppealResponse from(Appeal appeal) {
    return new AppealResponse(
        appeal.id().value().toString(),
        appeal.status().name(),
        appeal.outcomeExplanation().orElse(null),
        appeal.createdAt(),
        appeal.decidedAt().orElse(null));
  }
}
