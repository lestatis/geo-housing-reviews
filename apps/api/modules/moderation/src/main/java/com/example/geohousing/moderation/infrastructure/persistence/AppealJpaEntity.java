package com.example.geohousing.moderation.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistence row for one appeal.
 *
 * <p>{@code originalDeciderAccountId} is copied from the decision so the database can enforce the
 * different-decider rule as a single-row CHECK, rather than trusting a service to look it up.
 */
@Entity
@Table(schema = "moderation", name = "appeal")
class AppealJpaEntity {

  @Id private UUID id;

  @Column(name = "decision_id", nullable = false)
  private UUID decisionId;

  @Column(name = "appellant_account_id", nullable = false)
  private UUID appellantAccountId;

  @Column(name = "appeal_text", nullable = false)
  private String appealText;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "outcome_explanation")
  private String outcomeExplanation;

  @Column(name = "original_decider_account_id", nullable = false)
  private UUID originalDeciderAccountId;

  @Column(name = "decided_by_account_id")
  private UUID decidedByAccountId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  protected AppealJpaEntity() {
    // for JPA
  }

  AppealJpaEntity(
      UUID id,
      UUID decisionId,
      UUID appellantAccountId,
      String appealText,
      String status,
      String outcomeExplanation,
      UUID originalDeciderAccountId,
      UUID decidedByAccountId,
      Instant createdAt,
      Instant decidedAt,
      long version) {
    this.id = id;
    this.decisionId = decisionId;
    this.appellantAccountId = appellantAccountId;
    this.appealText = appealText;
    this.status = status;
    this.outcomeExplanation = outcomeExplanation;
    this.originalDeciderAccountId = originalDeciderAccountId;
    this.decidedByAccountId = decidedByAccountId;
    this.createdAt = createdAt;
    this.decidedAt = decidedAt;
    this.version = version;
  }

  UUID id() {
    return id;
  }

  UUID decisionId() {
    return decisionId;
  }

  UUID appellantAccountId() {
    return appellantAccountId;
  }

  String appealText() {
    return appealText;
  }

  String status() {
    return status;
  }

  String outcomeExplanation() {
    return outcomeExplanation;
  }

  UUID originalDeciderAccountId() {
    return originalDeciderAccountId;
  }

  UUID decidedByAccountId() {
    return decidedByAccountId;
  }

  Instant createdAt() {
    return createdAt;
  }

  Instant decidedAt() {
    return decidedAt;
  }

  long version() {
    return version;
  }

  void apply(String status, String outcomeExplanation, UUID decidedByAccountId, Instant decidedAt) {
    this.status = status;
    this.outcomeExplanation = outcomeExplanation;
    this.decidedByAccountId = decidedByAccountId;
    this.decidedAt = decidedAt;
  }
}
