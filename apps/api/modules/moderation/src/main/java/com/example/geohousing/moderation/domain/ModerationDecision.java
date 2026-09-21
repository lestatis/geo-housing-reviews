package com.example.geohousing.moderation.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What a moderator decided about a case, and why (docs/DOMAIN_MODEL.md ModerationDecision).
 *
 * <p>Immutable by design. An appeal must be able to show what was decided, by whom, and under which
 * policy version the content was judged (MODERATION.md), so a decision is never edited — a changed
 * outcome is a new decision. That is also why the type exposes no mutators and the persisted row
 * carries no {@code updated_at}.
 *
 * <p>Two explanations, never mixed:
 *
 * <ul>
 *   <li>{@code publicExplanation} is shown to the affected user and must be specific enough to let
 *       them correct the issue. Any action that costs them something requires one.
 *   <li>{@code internalNote} records abuse signals and reasoning for other moderators. It must
 *       never reach the user: MODERATION.md wants explanations that do not expose how detection
 *       works.
 * </ul>
 */
public final class ModerationDecision {

  private final ModerationDecisionId id;
  private final ModerationCaseId caseId;
  private final DecisionAction action;
  private final ReasonCode reasonCode;
  private final PolicyVersion policyVersion;
  private final String publicExplanation;
  private final String internalNote;
  private final Long affectedTargetVersion;
  private final ModeratorId decidedBy;
  private final Instant decidedAt;
  private final UUID createdRestrictionId;

  private ModerationDecision(
      ModerationDecisionId id,
      ModerationCaseId caseId,
      DecisionAction action,
      ReasonCode reasonCode,
      PolicyVersion policyVersion,
      String publicExplanation,
      String internalNote,
      Long affectedTargetVersion,
      ModeratorId decidedBy,
      Instant decidedAt,
      UUID createdRestrictionId) {
    this.id = Objects.requireNonNull(id, "id");
    this.caseId = Objects.requireNonNull(caseId, "caseId");
    this.action = Objects.requireNonNull(action, "action");
    this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
    this.policyVersion = Objects.requireNonNull(policyVersion, "policyVersion");
    this.publicExplanation = requireExplanationWhenAdverse(action, publicExplanation);
    this.internalNote = blankToNull(internalNote);
    this.affectedTargetVersion = affectedTargetVersion;
    this.decidedBy = Objects.requireNonNull(decidedBy, "decidedBy");
    this.decidedAt = Objects.requireNonNull(decidedAt, "decidedAt");
    this.createdRestrictionId = createdRestrictionId;
  }

  /**
   * Records a decision.
   *
   * @param affectedTargetVersion the version of the content that was judged, so an edit after the
   *     decision is visibly a different thing from what the moderator read; {@code null} only when
   *     the target carries no version
   */
  public static ModerationDecision record(
      ModerationDecisionId id,
      ModerationCaseId caseId,
      DecisionAction action,
      ReasonCode reasonCode,
      PolicyVersion policyVersion,
      String publicExplanation,
      String internalNote,
      Long affectedTargetVersion,
      ModeratorId decidedBy,
      Clock clock) {
    Objects.requireNonNull(clock, "clock");
    return new ModerationDecision(
        id,
        caseId,
        action,
        reasonCode,
        policyVersion,
        publicExplanation,
        internalNote,
        affectedTargetVersion,
        decidedBy,
        clock.instant(),
        null);
  }

  /** Rebuilds a decision from persisted state. Intended for persistence adapters only. */
  public static ModerationDecision reconstitute(
      ModerationDecisionId id,
      ModerationCaseId caseId,
      DecisionAction action,
      ReasonCode reasonCode,
      PolicyVersion policyVersion,
      String publicExplanation,
      String internalNote,
      Long affectedTargetVersion,
      ModeratorId decidedBy,
      Instant decidedAt,
      UUID createdRestrictionId) {
    return new ModerationDecision(
        id,
        caseId,
        action,
        reasonCode,
        policyVersion,
        publicExplanation,
        internalNote,
        affectedTargetVersion,
        decidedBy,
        decidedAt,
        createdRestrictionId);
  }

  /**
   * The same decision, now knowing which restriction it created.
   *
   * <p>Effects run before a decision is recorded (DECISION_LOG, 2026-07-29), so the identifier does
   * not exist when the decision is built. A copy rather than a setter: the decision is still
   * immutable, and the only thing that may learn this is the code that just caused it.
   */
  public ModerationDecision withCreatedRestriction(UUID restrictionId) {
    return new ModerationDecision(
        id,
        caseId,
        action,
        reasonCode,
        policyVersion,
        publicExplanation,
        internalNote,
        affectedTargetVersion,
        decidedBy,
        decidedAt,
        restrictionId);
  }

  /**
   * The restriction this decision placed, if it placed one.
   *
   * <p>Empty for almost every decision, and also for a {@code RESTRICT_ACCOUNT} that found the
   * account already restricted — that restriction belongs to whichever case created it, and
   * overturning this decision must not lift it.
   */
  public Optional<UUID> createdRestrictionId() {
    return Optional.ofNullable(createdRestrictionId);
  }

  private static String requireExplanationWhenAdverse(
      DecisionAction action, String publicExplanation) {
    String trimmed = blankToNull(publicExplanation);
    if (action != null && action.requiresPublicExplanation() && trimmed == null) {
      throw new IllegalArgumentException(
          "a " + action + " decision must tell the affected user why");
    }
    return trimmed;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public ModerationDecisionId id() {
    return id;
  }

  public ModerationCaseId caseId() {
    return caseId;
  }

  public DecisionAction action() {
    return action;
  }

  public ReasonCode reasonCode() {
    return reasonCode;
  }

  public PolicyVersion policyVersion() {
    return policyVersion;
  }

  /** The explanation shown to the affected user; absent for actions that take nothing away. */
  public Optional<String> publicExplanation() {
    return Optional.ofNullable(publicExplanation);
  }

  /** Moderator-only reasoning. Never include this in anything a user can read. */
  public Optional<String> internalNote() {
    return Optional.ofNullable(internalNote);
  }

  public Optional<Long> affectedTargetVersion() {
    return Optional.ofNullable(affectedTargetVersion);
  }

  public ModeratorId decidedBy() {
    return decidedBy;
  }

  public Instant decidedAt() {
    return decidedAt;
  }
}
