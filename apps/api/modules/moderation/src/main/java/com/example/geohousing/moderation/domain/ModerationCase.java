package com.example.geohousing.moderation.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One piece of content under review, however many people reported it (docs/DOMAIN_MODEL.md
 * ModerationCase; docs/MODERATION.md).
 *
 * <p>State machine: {@code OPEN → IN_REVIEW} (a moderator takes it) {@code → DECIDED} (a decision
 * is recorded) {@code → APPEALED} (the affected user appeals) {@code → CLOSED}. A case may also be
 * closed straight from {@code DECIDED} when nobody appeals.
 *
 * <p>Two deliberate restrictions:
 *
 * <ul>
 *   <li>A decision may only be recorded on a case that is {@code IN_REVIEW}, so every decision has
 *       a named moderator accountable for it. An unassigned case cannot produce an anonymous
 *       outcome.
 *   <li>A case may only be closed once it has been decided. Nothing lets a case disappear
 *       unexplained — the affected user is always owed a recorded reason, which is what makes an
 *       appeal possible at all.
 * </ul>
 *
 * <p>The target is referenced by opaque id: the content belongs to another module, and a decision's
 * effect on it is applied through that module's published contract, never by touching its tables.
 */
public final class ModerationCase {

  private final ModerationCaseId id;
  private final ModerationTargetRef target;
  private final CaseTrigger trigger;
  private ModerationCaseStatus status;
  private RiskLevel riskLevel;
  private ModeratorId assignedModerator;
  private final Instant openedAt;
  private Instant firstResponseAt;
  private Instant closedAt;
  private final Instant createdAt;
  private Instant updatedAt;
  private final long version;

  private ModerationCase(
      ModerationCaseId id,
      ModerationTargetRef target,
      CaseTrigger trigger,
      ModerationCaseStatus status,
      RiskLevel riskLevel,
      ModeratorId assignedModerator,
      Instant openedAt,
      Instant firstResponseAt,
      Instant closedAt,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    this.id = Objects.requireNonNull(id, "id");
    this.target = Objects.requireNonNull(target, "target");
    this.trigger = Objects.requireNonNull(trigger, "trigger");
    this.status = Objects.requireNonNull(status, "status");
    this.riskLevel = Objects.requireNonNull(riskLevel, "riskLevel");
    this.assignedModerator = assignedModerator;
    this.openedAt = Objects.requireNonNull(openedAt, "openedAt");
    this.firstResponseAt = firstResponseAt;
    this.closedAt = closedAt;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    this.version = version;
    checkInvariants();
  }

  /** Opens a case for a target nobody is working yet. */
  public static ModerationCase open(
      ModerationCaseId id,
      ModerationTargetRef target,
      CaseTrigger trigger,
      RiskLevel riskLevel,
      Clock clock) {
    Objects.requireNonNull(clock, "clock");
    Instant now = clock.instant();
    return new ModerationCase(
        id,
        target,
        trigger,
        ModerationCaseStatus.OPEN,
        riskLevel,
        null,
        now,
        null,
        null,
        now,
        now,
        0L);
  }

  /** Rebuilds a case from persisted state. Intended for persistence adapters only. */
  public static ModerationCase reconstitute(
      ModerationCaseId id,
      ModerationTargetRef target,
      CaseTrigger trigger,
      ModerationCaseStatus status,
      RiskLevel riskLevel,
      ModeratorId assignedModerator,
      Instant openedAt,
      Instant firstResponseAt,
      Instant closedAt,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    return new ModerationCase(
        id,
        target,
        trigger,
        status,
        riskLevel,
        assignedModerator,
        openedAt,
        firstResponseAt,
        closedAt,
        createdAt,
        updatedAt,
        version);
  }

  /**
   * A moderator takes the case. The first assignment stamps {@code firstResponseAt}, which is the
   * only honest measure of how long a reporter waited before a human looked.
   *
   * <p>Reassignment while in review is allowed — a moderator who recognises a conflict of interest
   * must be able to hand the case on (MODERATION.md: moderators disclose conflicts and can recuse).
   */
  public void assignTo(ModeratorId moderatorId, Clock clock) {
    if (status != ModerationCaseStatus.OPEN && status != ModerationCaseStatus.IN_REVIEW) {
      throw new IllegalModerationStateTransitionException(
          "only an OPEN or IN_REVIEW case can be assigned, was " + status);
    }
    Objects.requireNonNull(moderatorId, "moderatorId");
    this.assignedModerator = moderatorId;
    this.status = ModerationCaseStatus.IN_REVIEW;
    if (firstResponseAt == null) {
      this.firstResponseAt = clock.instant();
    }
    touch(clock);
  }

  /**
   * Records that a decision has been made on this case.
   *
   * <p>The decision itself is a separate immutable record; this only moves the case. Requiring
   * {@code IN_REVIEW} means no decision exists without a moderator accountable for it.
   */
  public void markDecided(Clock clock) {
    if (status != ModerationCaseStatus.IN_REVIEW) {
      throw new IllegalModerationStateTransitionException(
          "only an IN_REVIEW case can be decided, was " + status);
    }
    this.status = ModerationCaseStatus.DECIDED;
    touch(clock);
  }

  /** Records that the affected user has appealed the decision. */
  public void markAppealed(Clock clock) {
    if (status != ModerationCaseStatus.DECIDED) {
      throw new IllegalModerationStateTransitionException(
          "only a DECIDED case can be appealed, was " + status);
    }
    this.status = ModerationCaseStatus.APPEALED;
    touch(clock);
  }

  /**
   * Closes a settled case, freeing the one-live-case slot so the same content can be reported again
   * later. Only a decided or appealed case may close: a case must never vanish unexplained.
   */
  public void close(Clock clock) {
    if (status != ModerationCaseStatus.DECIDED && status != ModerationCaseStatus.APPEALED) {
      throw new IllegalModerationStateTransitionException(
          "only a DECIDED or APPEALED case can be closed, was " + status);
    }
    this.status = ModerationCaseStatus.CLOSED;
    this.closedAt = clock.instant();
    touch(clock);
  }

  /** Raises or lowers how much care the case needs; possible at any point before it closes. */
  public void reclassify(RiskLevel riskLevel, Clock clock) {
    if (status.isTerminal()) {
      throw new IllegalModerationStateTransitionException("a CLOSED case cannot be reclassified");
    }
    this.riskLevel = Objects.requireNonNull(riskLevel, "riskLevel");
    touch(clock);
  }

  /** Whether this case still occupies the one-live-case slot for its target. */
  public boolean isLive() {
    return !status.isTerminal();
  }

  /** Whether a new report about this target should be attached here rather than opening a case. */
  public boolean acceptsReports() {
    return status == ModerationCaseStatus.OPEN || status == ModerationCaseStatus.IN_REVIEW;
  }

  private void touch(Clock clock) {
    this.updatedAt = clock.instant();
    checkInvariants();
  }

  private void checkInvariants() {
    if (status == ModerationCaseStatus.OPEN && assignedModerator != null) {
      throw new IllegalStateException("an OPEN case has nobody assigned");
    }
    if (status != ModerationCaseStatus.OPEN && assignedModerator == null) {
      throw new IllegalStateException("a case past OPEN records the moderator accountable for it");
    }
    if (status.isTerminal() == (closedAt == null)) {
      throw new IllegalStateException("a case is CLOSED exactly when it records when it closed");
    }
    if (closedAt != null && closedAt.isBefore(openedAt)) {
      throw new IllegalStateException("a case cannot close before it opened");
    }
  }

  public ModerationCaseId id() {
    return id;
  }

  public ModerationTargetRef target() {
    return target;
  }

  public CaseTrigger trigger() {
    return trigger;
  }

  public ModerationCaseStatus status() {
    return status;
  }

  public RiskLevel riskLevel() {
    return riskLevel;
  }

  public Optional<ModeratorId> assignedModerator() {
    return Optional.ofNullable(assignedModerator);
  }

  public Instant openedAt() {
    return openedAt;
  }

  public Optional<Instant> firstResponseAt() {
    return Optional.ofNullable(firstResponseAt);
  }

  public Optional<Instant> closedAt() {
    return Optional.ofNullable(closedAt);
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public long version() {
    return version;
  }
}
