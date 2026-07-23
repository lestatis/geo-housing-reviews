package com.example.geohousing.verification.infrastructure.persistence;

import com.example.geohousing.verification.domain.RelationshipClaim;
import com.example.geohousing.verification.domain.VerificationMethod;
import com.example.geohousing.verification.domain.VerificationStatus;
import com.example.geohousing.verification.domain.VerificationTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * The verification case aggregate root — a single row. {@code account_id} and {@code property_id}
 * are plain UUID columns, not associations: they point into other modules, which own their tables.
 */
@Entity
@Table(schema = "verification", name = "verification_case")
class VerificationCaseJpaEntity {

  @Id private UUID id;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(name = "property_id", nullable = false)
  private UUID propertyId;

  @Enumerated(EnumType.STRING)
  @Column(name = "relationship_claim", nullable = false, length = 30)
  private RelationshipClaim relationshipClaim;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private VerificationMethod method;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private VerificationStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private VerificationTier tier;

  @Column(name = "decision_reason_code")
  private String decisionReasonCode;

  @Column(name = "policy_version", nullable = false)
  private int policyVersion;

  @Column(name = "verified_at")
  private Instant verifiedAt;

  @Column(name = "valid_through")
  private Instant validThrough;

  @Column(name = "decided_by")
  private UUID decidedBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected VerificationCaseJpaEntity() {
    // for JPA
  }

  VerificationCaseJpaEntity(
      UUID id,
      UUID accountId,
      UUID propertyId,
      RelationshipClaim relationshipClaim,
      VerificationMethod method,
      VerificationStatus status,
      VerificationTier tier,
      String decisionReasonCode,
      int policyVersion,
      Instant verifiedAt,
      Instant validThrough,
      UUID decidedBy,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    this.id = id;
    this.accountId = accountId;
    this.propertyId = propertyId;
    this.relationshipClaim = relationshipClaim;
    this.method = method;
    this.status = status;
    this.tier = tier;
    this.decisionReasonCode = decisionReasonCode;
    this.policyVersion = policyVersion;
    this.verifiedAt = verifiedAt;
    this.validThrough = validThrough;
    this.decidedBy = decidedBy;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.version = version;
  }

  /** Copies the mutable decision fields from a changed aggregate onto this managed entity. */
  void apply(
      VerificationStatus newStatus,
      VerificationTier newTier,
      String newDecisionReasonCode,
      Instant newVerifiedAt,
      Instant newValidThrough,
      UUID newDecidedBy,
      Instant newUpdatedAt) {
    this.status = newStatus;
    this.tier = newTier;
    this.decisionReasonCode = newDecisionReasonCode;
    this.verifiedAt = newVerifiedAt;
    this.validThrough = newValidThrough;
    this.decidedBy = newDecidedBy;
    this.updatedAt = newUpdatedAt;
  }

  UUID id() {
    return id;
  }

  UUID accountId() {
    return accountId;
  }

  UUID propertyId() {
    return propertyId;
  }

  RelationshipClaim relationshipClaim() {
    return relationshipClaim;
  }

  VerificationMethod method() {
    return method;
  }

  VerificationStatus status() {
    return status;
  }

  VerificationTier tier() {
    return tier;
  }

  String decisionReasonCode() {
    return decisionReasonCode;
  }

  int policyVersion() {
    return policyVersion;
  }

  Instant verifiedAt() {
    return verifiedAt;
  }

  Instant validThrough() {
    return validThrough;
  }

  UUID decidedBy() {
    return decidedBy;
  }

  Instant createdAt() {
    return createdAt;
  }

  Instant updatedAt() {
    return updatedAt;
  }

  long version() {
    return version;
  }
}
