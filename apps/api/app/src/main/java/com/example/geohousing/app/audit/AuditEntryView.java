package com.example.geohousing.app.audit;

import com.example.geohousing.shared.audit.AuditEntry;
import java.time.Instant;

/**
 * One line of the timeline.
 *
 * <p>Carries exactly what {@link AuditEntry} does and nothing more. In particular no moderator's
 * internal note and no public explanation: the timeline says an action happened, and the module
 * that owns the subject says what it contained.
 */
public record AuditEntryView(
    Instant at,
    String actorAccountId,
    String module,
    String action,
    String subjectType,
    String subjectId,
    String outcome,
    String reason) {

  static AuditEntryView from(AuditEntry entry) {
    return new AuditEntryView(
        entry.at(),
        entry.actorAccountId().map(UuidText::of).orElse(null),
        entry.module(),
        entry.action(),
        entry.subjectType(),
        entry.subjectId().orElse(null),
        entry.outcome().orElse(null),
        entry.reason().orElse(null));
  }

  private static final class UuidText {
    private static String of(java.util.UUID id) {
      return id.toString();
    }
  }
}
