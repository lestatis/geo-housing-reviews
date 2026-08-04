package com.example.geohousing.app.audit;

import com.example.geohousing.shared.audit.AuditCursor;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What an administrator is asking the timeline for.
 *
 * <p>A window is mandatory. The alternative — "everything" — is a table scan across five
 * append-only tables that only ever grow, triggerable by anyone with the role.
 *
 * <p>{@code from} is inclusive and {@code until} is exclusive. Every adapter's SQL is written that
 * way, so a caller translating a calendar date into an instant must translate "up to and including
 * the 4th" into the start of the 5th — anything else drops the last fraction of a second of the
 * day, which is a real place for an event to be.
 *
 * @param after where a previous page left off, or {@code null} for the first page
 */
public record AuditQuery(
    Instant from, Instant until, AuditCursor after, UUID actorAccountId, int limit) {

  public static final int DEFAULT_LIMIT = 50;
  public static final int MAX_LIMIT = 200;

  public AuditQuery {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(until, "until");
    if (until.isBefore(from)) {
      // Refused rather than answered with nothing: an empty timeline reads as "nothing happened",
      // which is the one answer an audit log must never give by accident.
      throw new InvalidAuditQueryException(
          "until", "BEFORE_SINCE", "The window ends before it starts.");
    }
    if (after != null && (after.at().isAfter(until) || after.at().isBefore(from))) {
      // A cursor names a position, not a window. Pairing one with a window it did not come from
      // would answer a question nobody asked.
      throw new InvalidAuditQueryException(
          "cursor", "NOT_FROM_WINDOW", "This cursor does not belong to this window.");
    }
    limit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
  }

  /**
   * Where this page starts.
   *
   * <p>The first page starts at the window's exclusive end, with nothing at that instant yet
   * excluded — which is what makes a first page and a continuation the same query.
   */
  public AuditCursor startsAt() {
    return after == null ? AuditCursor.startingAt(until) : after;
  }

  /** Absent means everyone's actions, not nobody's. */
  public Optional<UUID> actor() {
    return Optional.ofNullable(actorAccountId);
  }
}
