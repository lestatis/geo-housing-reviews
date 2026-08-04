package com.example.geohousing.app.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What an administrator is asking the timeline for.
 *
 * <p>A window is mandatory. The alternative — "everything" — is a table scan across five
 * append-only tables that only ever grow, triggerable by anyone with the role.
 */
public record AuditQuery(Instant from, Instant until, UUID actorAccountId, int limit) {

  public static final int DEFAULT_LIMIT = 50;
  public static final int MAX_LIMIT = 200;

  public AuditQuery {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(until, "until");
    if (until.isBefore(from)) {
      // Refused rather than answered with nothing: an empty timeline reads as "nothing happened",
      // which is the one answer an audit log must never give by accident.
      throw new IllegalArgumentException("the window ends before it starts");
    }
    limit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
  }

  /** Absent means everyone's actions, not nobody's. */
  public Optional<UUID> actor() {
    return Optional.ofNullable(actorAccountId);
  }
}
