package com.example.geohousing.verification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An append-only record that a decision was made (or attempted) on a verification case. Every
 * decision carries a reason code — no unexplained verification action is allowed
 * (TRUST_VERIFICATION.md §7) — and the case id is recorded even when no such case exists, so a
 * failed attempt is still auditable.
 *
 * <p>The actor is absent only for a system-initiated {@code EXPIRE}; a person's decision always
 * records who made it.
 */
public final class VerificationDecisionAuditEvent {

  private final UUID id;
  private final UUID actorAccountId;
  private final VerificationDecisionAction action;
  private final VerificationCaseId caseId;
  private final String reasonCode;
  private final VerificationDecisionOutcome outcome;
  private final Instant occurredAt;

  private VerificationDecisionAuditEvent(
      UUID id,
      UUID actorAccountId,
      VerificationDecisionAction action,
      VerificationCaseId caseId,
      String reasonCode,
      VerificationDecisionOutcome outcome,
      Instant occurredAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.action = Objects.requireNonNull(action, "action");
    this.caseId = Objects.requireNonNull(caseId, "caseId");
    this.reasonCode = requireReasonCode(reasonCode);
    this.outcome = Objects.requireNonNull(outcome, "outcome");
    this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    if (action != VerificationDecisionAction.EXPIRE && actorAccountId == null) {
      throw new IllegalArgumentException("a human decision must record its actor");
    }
    this.actorAccountId = actorAccountId;
  }

  /** Records a person's decision that took effect. */
  public static VerificationDecisionAuditEvent applied(
      UUID actorAccountId,
      VerificationDecisionAction action,
      VerificationCaseId caseId,
      String reasonCode,
      Instant occurredAt) {
    return new VerificationDecisionAuditEvent(
        UUID.randomUUID(),
        Objects.requireNonNull(actorAccountId, "actorAccountId"),
        action,
        caseId,
        reasonCode,
        VerificationDecisionOutcome.APPLIED,
        occurredAt);
  }

  /** Records a system-initiated expiry, which has no human actor. */
  public static VerificationDecisionAuditEvent systemExpiry(
      VerificationCaseId caseId, String reasonCode, Instant occurredAt) {
    return new VerificationDecisionAuditEvent(
        UUID.randomUUID(),
        null,
        VerificationDecisionAction.EXPIRE,
        caseId,
        reasonCode,
        VerificationDecisionOutcome.APPLIED,
        occurredAt);
  }

  /** Records a decision attempted against a case that does not exist. */
  public static VerificationDecisionAuditEvent notFound(
      UUID actorAccountId,
      VerificationDecisionAction action,
      VerificationCaseId caseId,
      String reasonCode,
      Instant occurredAt) {
    return new VerificationDecisionAuditEvent(
        UUID.randomUUID(),
        Objects.requireNonNull(actorAccountId, "actorAccountId"),
        action,
        caseId,
        reasonCode,
        VerificationDecisionOutcome.NOT_FOUND,
        occurredAt);
  }

  private static String requireReasonCode(String reasonCode) {
    if (reasonCode == null || reasonCode.isBlank()) {
      throw new IllegalArgumentException("every verification decision requires a reason code");
    }
    String trimmed = reasonCode.trim();
    if (trimmed.length() > 64) {
      throw new IllegalArgumentException("reasonCode must not exceed 64 characters");
    }
    return trimmed;
  }

  public UUID id() {
    return id;
  }

  public Optional<UUID> actorAccountId() {
    return Optional.ofNullable(actorAccountId);
  }

  public VerificationDecisionAction action() {
    return action;
  }

  public VerificationCaseId caseId() {
    return caseId;
  }

  public String reasonCode() {
    return reasonCode;
  }

  public VerificationDecisionOutcome outcome() {
    return outcome;
  }

  public Instant occurredAt() {
    return occurredAt;
  }
}
