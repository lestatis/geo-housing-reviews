package com.example.geohousing.verification.infrastructure.persistence;

import com.example.geohousing.verification.domain.VerificationDecisionAction;
import com.example.geohousing.verification.domain.VerificationDecisionOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One append-only verification-decision audit row. Inserted, never updated or deleted. */
@Entity
@Table(schema = "verification", name = "verification_decision_audit_event")
class VerificationDecisionAuditEventJpaEntity {

  @Id private UUID id;

  @Column(name = "actor_account_id")
  private UUID actorAccountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private VerificationDecisionAction action;

  @Column(name = "case_id", nullable = false)
  private UUID caseId;

  @Column(name = "reason_code", nullable = false, length = 64)
  private String reasonCode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private VerificationDecisionOutcome outcome;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected VerificationDecisionAuditEventJpaEntity() {
    // for JPA
  }

  VerificationDecisionAuditEventJpaEntity(
      UUID id,
      UUID actorAccountId,
      VerificationDecisionAction action,
      UUID caseId,
      String reasonCode,
      VerificationDecisionOutcome outcome,
      Instant createdAt) {
    this.id = id;
    this.actorAccountId = actorAccountId;
    this.action = action;
    this.caseId = caseId;
    this.reasonCode = reasonCode;
    this.outcome = outcome;
    this.createdAt = createdAt;
  }

  UUID id() {
    return id;
  }

  UUID actorAccountId() {
    return actorAccountId;
  }

  VerificationDecisionAction action() {
    return action;
  }

  UUID caseId() {
    return caseId;
  }

  String reasonCode() {
    return reasonCode;
  }

  VerificationDecisionOutcome outcome() {
    return outcome;
  }

  Instant createdAt() {
    return createdAt;
  }
}
