package com.example.geohousing.moderation.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistence row for one report. The reporter's account id is stored so abuse of the channel is
 * traceable; it is never part of a public representation.
 */
@Entity
@Table(schema = "moderation", name = "report")
class ReportJpaEntity {

  @Id private UUID id;

  @Column(name = "target_type", nullable = false, length = 20)
  private String targetType;

  @Column(name = "target_id", nullable = false)
  private UUID targetId;

  @Column(name = "reporter_account_id", nullable = false)
  private UUID reporterAccountId;

  @Column(name = "category", nullable = false, length = 40)
  private String category;

  @Column(name = "description")
  private String description;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "case_id")
  private UUID caseId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected ReportJpaEntity() {
    // for JPA
  }

  ReportJpaEntity(
      UUID id,
      String targetType,
      UUID targetId,
      UUID reporterAccountId,
      String category,
      String description,
      String status,
      UUID caseId,
      Instant createdAt) {
    this.id = id;
    this.targetType = targetType;
    this.targetId = targetId;
    this.reporterAccountId = reporterAccountId;
    this.category = category;
    this.description = description;
    this.status = status;
    this.caseId = caseId;
    this.createdAt = createdAt;
  }

  UUID id() {
    return id;
  }

  String targetType() {
    return targetType;
  }

  UUID targetId() {
    return targetId;
  }

  UUID reporterAccountId() {
    return reporterAccountId;
  }

  String category() {
    return category;
  }

  String description() {
    return description;
  }

  String status() {
    return status;
  }

  UUID caseId() {
    return caseId;
  }

  Instant createdAt() {
    return createdAt;
  }

  void apply(String status, UUID caseId) {
    this.status = status;
    this.caseId = caseId;
  }
}
