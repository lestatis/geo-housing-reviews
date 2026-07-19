package com.example.geohousing.identity.infrastructure.persistence;

import com.example.geohousing.identity.domain.AdminAuditAction;
import com.example.geohousing.identity.domain.AdminAuditOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "identity", name = "admin_audit_event")
class AdminAuditEventJpaEntity {

  @Id private UUID id;

  @Column(name = "admin_account_id", nullable = false)
  private UUID adminAccountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 64)
  private AdminAuditAction action;

  @Column(name = "target_account_id")
  private UUID targetAccountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AdminAuditOutcome outcome;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected AdminAuditEventJpaEntity() {}

  AdminAuditEventJpaEntity(
      UUID id,
      UUID adminAccountId,
      AdminAuditAction action,
      UUID targetAccountId,
      AdminAuditOutcome outcome,
      Instant createdAt) {
    this.id = id;
    this.adminAccountId = adminAccountId;
    this.action = action;
    this.targetAccountId = targetAccountId;
    this.outcome = outcome;
    this.createdAt = createdAt;
  }

  UUID id() {
    return id;
  }

  UUID adminAccountId() {
    return adminAccountId;
  }

  AdminAuditAction action() {
    return action;
  }

  UUID targetAccountId() {
    return targetAccountId;
  }

  AdminAuditOutcome outcome() {
    return outcome;
  }

  Instant createdAt() {
    return createdAt;
  }
}
