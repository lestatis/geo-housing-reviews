package com.example.geohousing.verification.infrastructure.persistence;

import com.example.geohousing.verification.domain.EvidenceAccessAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One append-only evidence access row. Inserted, never updated or deleted. */
@Entity
@Table(schema = "verification", name = "verification_evidence_access_event")
class EvidenceAccessEventJpaEntity {

  @Id private UUID id;

  @Column(name = "evidence_id", nullable = false)
  private UUID evidenceId;

  @Column(name = "accessor_account_id")
  private UUID accessorAccountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EvidenceAccessAction action;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected EvidenceAccessEventJpaEntity() {
    // for JPA
  }

  EvidenceAccessEventJpaEntity(
      UUID id,
      UUID evidenceId,
      UUID accessorAccountId,
      EvidenceAccessAction action,
      Instant createdAt) {
    this.id = id;
    this.evidenceId = evidenceId;
    this.accessorAccountId = accessorAccountId;
    this.action = action;
    this.createdAt = createdAt;
  }
}
