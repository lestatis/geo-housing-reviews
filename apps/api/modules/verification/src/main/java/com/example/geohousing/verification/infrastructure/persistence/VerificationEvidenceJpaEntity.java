package com.example.geohousing.verification.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Evidence metadata — never the document bytes, which live in the quarantined object store
 * (ADR-0008). {@code case_id} is a plain UUID column rather than an association: evidence has its
 * own lifecycle and is loaded on its own, not dragged along with every case.
 */
@Entity
@Table(schema = "verification", name = "verification_evidence")
class VerificationEvidenceJpaEntity {

  @Id private UUID id;

  @Column(name = "case_id", nullable = false)
  private UUID caseId;

  @Column(name = "storage_key", nullable = false)
  private String storageKey;

  @Column(name = "content_type", nullable = false, length = 100)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  // V5.2 declares this CHAR(64) — a checksum is exactly 64 hex characters, never shorter. Hibernate
  // maps a String to varchar by default, so the column definition is stated explicitly to keep
  // ddl-auto=validate green rather than loosening the schema to match the mapping.
  @Column(nullable = false, length = 64, columnDefinition = "bpchar")
  private String sha256;

  @Column(name = "retention_deadline", nullable = false)
  private Instant retentionDeadline;

  @Column(name = "uploaded_at", nullable = false)
  private Instant uploadedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  protected VerificationEvidenceJpaEntity() {
    // for JPA
  }

  VerificationEvidenceJpaEntity(
      UUID id,
      UUID caseId,
      String storageKey,
      String contentType,
      long sizeBytes,
      String sha256,
      Instant retentionDeadline,
      Instant uploadedAt,
      Instant deletedAt) {
    this.id = id;
    this.caseId = caseId;
    this.storageKey = storageKey;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.sha256 = sha256;
    this.retentionDeadline = retentionDeadline;
    this.uploadedAt = uploadedAt;
    this.deletedAt = deletedAt;
  }

  /**
   * Stamps the deletion time. The only mutation this row ever undergoes — everything else about a
   * piece of evidence is fixed at upload.
   */
  void markDeleted(Instant when) {
    this.deletedAt = when;
  }

  UUID id() {
    return id;
  }

  UUID caseId() {
    return caseId;
  }

  String storageKey() {
    return storageKey;
  }

  String contentType() {
    return contentType;
  }

  long sizeBytes() {
    return sizeBytes;
  }

  String sha256() {
    return sha256;
  }

  Instant retentionDeadline() {
    return retentionDeadline;
  }

  Instant uploadedAt() {
    return uploadedAt;
  }

  Instant deletedAt() {
    return deletedAt;
  }
}
