package com.example.geohousing.shared.audit;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One privileged action, as every module describes it.
 *
 * <p>Five modules already record the same six facts in five differently-named tables — when, who,
 * which action, on what, how it ended, and sometimes why. This is the vocabulary they answer in, so
 * a merged timeline is a merge rather than five translations.
 *
 * <p>It is deliberately thin. An audit view says <em>that</em> something happened; the module that
 * owns the subject says what it contains. Notably absent is anything a moderator wrote for other
 * moderators: an internal note lives on its case, and a timeline is not a reason to widen where it
 * can be read.
 *
 * <p>Carries the source row's id, which is not for display: it is the tiebreaker that makes the
 * merged order total, and therefore what lets a cursor resume exactly where it left off.
 *
 * <p>First occupant of {@code shared-kernel}. It earns the place by having five current callers
 * rather than an anticipated one, and it has no behaviour beyond validation and an ordering — the
 * next type that wants to live here should have to argue as hard.
 *
 * <p>A class rather than a record because half the fields are genuinely optional, and {@code
 * Optional} accessors read better at five call sites than null checks do. Same shape as {@code
 * UserRestriction} and {@code AdminAuditEvent} in the modules that will populate it.
 */
public final class AuditEntry {

  /**
   * The order a timeline is read in, and a total one.
   *
   * <p>Time alone is not enough: five sources can record something in the same millisecond, and a
   * merge that reordered itself between reads would make cursor paging skip or repeat entries.
   * Module then id break every tie the same way each time.
   */
  public static final Comparator<AuditEntry> NEWEST_FIRST =
      Comparator.comparing(AuditEntry::at)
          .reversed()
          .thenComparing(AuditEntry::module)
          .thenComparing(Comparator.comparing(AuditEntry::id, AuditCursor.ID_ORDER).reversed());

  private final UUID id;
  private final Instant at;
  private final UUID actorAccountId;
  private final String module;
  private final String action;
  private final String subjectType;
  private final String subjectId;
  private final String outcome;
  private final String reason;

  public AuditEntry(
      UUID id,
      Instant at,
      UUID actorAccountId,
      String module,
      String action,
      String subjectType,
      String subjectId,
      String outcome,
      String reason) {
    this.id = Objects.requireNonNull(id, "id");
    this.at = Objects.requireNonNull(at, "at");
    this.actorAccountId = actorAccountId;
    this.module = required(module, "module");
    this.action = required(action, "action");
    this.subjectType = required(subjectType, "subjectType");
    this.subjectId = subjectId;
    this.outcome = outcome;
    this.reason = reason;
  }

  /** The source row's own identifier — the tiebreaker that makes the order total. */
  public UUID id() {
    return id;
  }

  public Instant at() {
    return at;
  }

  /**
   * Absent for actions the system took on its own — verification expires a lapsed badge on a
   * schedule, and recording a fabricated actor would be worse than recording none.
   */
  public Optional<UUID> actorAccountId() {
    return Optional.ofNullable(actorAccountId);
  }

  /** Which module recorded this, so a reader knows which screen tells the rest of the story. */
  public String module() {
    return module;
  }

  public String action() {
    return action;
  }

  public String subjectType() {
    return subjectType;
  }

  /** Absent where the action names no single subject, or the subject no longer exists. */
  public Optional<String> subjectId() {
    return Optional.ofNullable(subjectId);
  }

  public Optional<String> outcome() {
    return Optional.ofNullable(outcome);
  }

  /** Present where the action required a reason code; a view of an account requires none. */
  public Optional<String> reason() {
    return Optional.ofNullable(reason);
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
