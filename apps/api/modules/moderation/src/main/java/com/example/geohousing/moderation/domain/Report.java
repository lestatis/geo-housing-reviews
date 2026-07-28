package com.example.geohousing.moderation.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One person's account of what is wrong with a piece of content (docs/DOMAIN_MODEL.md Report).
 *
 * <p>A report is evidence for a case, never a decision. MODERATION.md is explicit that a "false
 * claim" report does not automatically remove anything — a moderator still has to judge whether the
 * content is opinion, first-hand experience, a verifiable allegation, or fabrication. Nothing here
 * changes what any reader sees.
 *
 * <p>Lifecycle: {@code OPEN} on arrival, {@code LINKED} once attached to the case it feeds, then
 * {@code RESOLVED} (the concern was acted on) or {@code DISMISSED} (it was not). While OPEN or
 * LINKED it holds its reporter's one slot for this target, so the same account cannot file the same
 * complaint repeatedly; once terminal, a genuinely new problem with the same content can be raised.
 */
public final class Report {

  private final ReportId id;
  private final ModerationTargetRef target;
  private final ReporterId reporterId;
  private final ReportCategory category;
  private final String description;
  private ReportStatus status;
  private ModerationCaseId caseId;
  private final Instant createdAt;

  private Report(
      ReportId id,
      ModerationTargetRef target,
      ReporterId reporterId,
      ReportCategory category,
      String description,
      ReportStatus status,
      ModerationCaseId caseId,
      Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.target = Objects.requireNonNull(target, "target");
    this.reporterId = Objects.requireNonNull(reporterId, "reporterId");
    this.category = Objects.requireNonNull(category, "category");
    this.description = normalizeDescription(category, description);
    this.status = Objects.requireNonNull(status, "status");
    this.caseId = caseId;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    checkInvariants();
  }

  /** Files a new report, not yet attached to a case. */
  public static Report file(
      ReportId id,
      ModerationTargetRef target,
      ReporterId reporterId,
      ReportCategory category,
      String description,
      Clock clock) {
    Objects.requireNonNull(clock, "clock");
    return new Report(
        id, target, reporterId, category, description, ReportStatus.OPEN, null, clock.instant());
  }

  /** Rebuilds a report from persisted state. Intended for persistence adapters only. */
  public static Report reconstitute(
      ReportId id,
      ModerationTargetRef target,
      ReporterId reporterId,
      ReportCategory category,
      String description,
      ReportStatus status,
      ModerationCaseId caseId,
      Instant createdAt) {
    return new Report(id, target, reporterId, category, description, status, caseId, createdAt);
  }

  /**
   * Attaches this report to the case it is evidence for.
   *
   * <p>Many reports about one target converge on one case; this is the link that makes that true.
   */
  public void linkTo(ModerationCaseId moderationCaseId) {
    if (status != ReportStatus.OPEN) {
      throw new IllegalModerationStateTransitionException(
          "only an OPEN report can be linked to a case, was " + status);
    }
    this.caseId = Objects.requireNonNull(moderationCaseId, "moderationCaseId");
    this.status = ReportStatus.LINKED;
    checkInvariants();
  }

  /** Closes the report out because its concern was acted on. */
  public void resolve() {
    requireLinked("resolved");
    this.status = ReportStatus.RESOLVED;
    checkInvariants();
  }

  /**
   * Closes the report out because its concern was not upheld. Dismissal is about this report, not
   * about the reporter: it carries no penalty and does not stop them reporting something else.
   */
  public void dismiss() {
    requireLinked("dismissed");
    this.status = ReportStatus.DISMISSED;
    checkInvariants();
  }

  /** Whether this report still holds its reporter's one slot for this target. */
  public boolean isLive() {
    return status.isLive();
  }

  private void requireLinked(String action) {
    if (status != ReportStatus.LINKED) {
      throw new IllegalModerationStateTransitionException(
          "only a LINKED report can be " + action + ", was " + status);
    }
  }

  private static String normalizeDescription(ReportCategory category, String description) {
    String trimmed = description == null || description.isBlank() ? null : description.trim();
    if (category != null && category.requiresDescription() && trimmed == null) {
      throw new IllegalArgumentException(
          "a report of category " + category + " must describe the problem");
    }
    return trimmed;
  }

  private void checkInvariants() {
    if (status != ReportStatus.OPEN && caseId == null) {
      throw new IllegalStateException("a report past intake is attached to the case it feeds");
    }
  }

  public ReportId id() {
    return id;
  }

  public ModerationTargetRef target() {
    return target;
  }

  public ReporterId reporterId() {
    return reporterId;
  }

  public ReportCategory category() {
    return category;
  }

  public Optional<String> description() {
    return Optional.ofNullable(description);
  }

  public ReportStatus status() {
    return status;
  }

  public Optional<ModerationCaseId> caseId() {
    return Optional.ofNullable(caseId);
  }

  public Instant createdAt() {
    return createdAt;
  }
}
