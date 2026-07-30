package com.example.geohousing.moderation.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistence row for one decision.
 *
 * <p>Append-only, and shaped to make that hard to violate: there is no mutator and no
 * {@code @Version}, matching a table with no {@code updated_at}. An appeal has to be able to show
 * what was decided, not what the decision later became.
 */
@Entity
@Table(schema = "moderation", name = "moderation_decision")
class ModerationDecisionJpaEntity {

  @Id private UUID id;

  @Column(name = "case_id", nullable = false)
  private UUID caseId;

  @Column(name = "action", nullable = false, length = 30)
  private String action;

  @Column(name = "reason_code", nullable = false, length = 64)
  private String reasonCode;

  @Column(name = "policy_version", nullable = false)
  private int policyVersion;

  @Column(name = "public_explanation")
  private String publicExplanation;

  @Column(name = "internal_note")
  private String internalNote;

  @Column(name = "affected_target_version")
  private Long affectedTargetVersion;

  @Column(name = "decided_by_account_id", nullable = false)
  private UUID decidedByAccountId;

  @Column(name = "decided_at", nullable = false)
  private Instant decidedAt;

  protected ModerationDecisionJpaEntity() {
    // for JPA
  }

  ModerationDecisionJpaEntity(
      UUID id,
      UUID caseId,
      String action,
      String reasonCode,
      int policyVersion,
      String publicExplanation,
      String internalNote,
      Long affectedTargetVersion,
      UUID decidedByAccountId,
      Instant decidedAt) {
    this.id = id;
    this.caseId = caseId;
    this.action = action;
    this.reasonCode = reasonCode;
    this.policyVersion = policyVersion;
    this.publicExplanation = publicExplanation;
    this.internalNote = internalNote;
    this.affectedTargetVersion = affectedTargetVersion;
    this.decidedByAccountId = decidedByAccountId;
    this.decidedAt = decidedAt;
  }

  UUID id() {
    return id;
  }

  UUID caseId() {
    return caseId;
  }

  String action() {
    return action;
  }

  String reasonCode() {
    return reasonCode;
  }

  int policyVersion() {
    return policyVersion;
  }

  String publicExplanation() {
    return publicExplanation;
  }

  String internalNote() {
    return internalNote;
  }

  Long affectedTargetVersion() {
    return affectedTargetVersion;
  }

  UUID decidedByAccountId() {
    return decidedByAccountId;
  }

  Instant decidedAt() {
    return decidedAt;
  }
}
