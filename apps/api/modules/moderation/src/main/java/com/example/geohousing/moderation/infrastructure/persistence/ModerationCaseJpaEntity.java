package com.example.geohousing.moderation.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Persistence row for one moderation case. */
@Entity
@Table(schema = "moderation", name = "moderation_case")
class ModerationCaseJpaEntity {

  @Id private UUID id;

  @Column(name = "target_type", nullable = false, length = 20)
  private String targetType;

  @Column(name = "target_id", nullable = false)
  private UUID targetId;

  @Column(name = "trigger_source", nullable = false, length = 20)
  private String triggerSource;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "risk_level", nullable = false, length = 20)
  private String riskLevel;

  @Column(name = "assigned_moderator_account_id")
  private UUID assignedModeratorAccountId;

  @Column(name = "opened_at", nullable = false)
  private Instant openedAt;

  @Column(name = "first_response_at")
  private Instant firstResponseAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // The case is the one aggregate here that several moderators can touch at once, so it carries the
  // optimistic lock the schema reserved for it.
  @Version
  @Column(name = "version", nullable = false)
  private long version;

  protected ModerationCaseJpaEntity() {
    // for JPA
  }

  ModerationCaseJpaEntity(
      UUID id,
      String targetType,
      UUID targetId,
      String triggerSource,
      String status,
      String riskLevel,
      UUID assignedModeratorAccountId,
      Instant openedAt,
      Instant firstResponseAt,
      Instant closedAt,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    this.id = id;
    this.targetType = targetType;
    this.targetId = targetId;
    this.triggerSource = triggerSource;
    this.status = status;
    this.riskLevel = riskLevel;
    this.assignedModeratorAccountId = assignedModeratorAccountId;
    this.openedAt = openedAt;
    this.firstResponseAt = firstResponseAt;
    this.closedAt = closedAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.version = version;
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

  String triggerSource() {
    return triggerSource;
  }

  String status() {
    return status;
  }

  String riskLevel() {
    return riskLevel;
  }

  UUID assignedModeratorAccountId() {
    return assignedModeratorAccountId;
  }

  Instant openedAt() {
    return openedAt;
  }

  Instant firstResponseAt() {
    return firstResponseAt;
  }

  Instant closedAt() {
    return closedAt;
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

  void apply(
      String status,
      String riskLevel,
      UUID assignedModeratorAccountId,
      Instant firstResponseAt,
      Instant closedAt,
      Instant updatedAt) {
    this.status = status;
    this.riskLevel = riskLevel;
    this.assignedModeratorAccountId = assignedModeratorAccountId;
    this.firstResponseAt = firstResponseAt;
    this.closedAt = closedAt;
    this.updatedAt = updatedAt;
  }
}
