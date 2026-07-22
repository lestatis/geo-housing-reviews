package com.example.geohousing.reviews.infrastructure.persistence;

import com.example.geohousing.reviews.domain.ReviewModerationAction;
import com.example.geohousing.reviews.domain.ReviewModerationOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One append-only moderation audit row. Inserted, never updated or deleted. */
@Entity
@Table(schema = "reviews", name = "review_moderation_audit_event")
class ReviewModerationAuditEventJpaEntity {

  @Id private UUID id;

  @Column(name = "moderator_account_id", nullable = false)
  private UUID moderatorAccountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ReviewModerationAction action;

  @Column(name = "review_id", nullable = false)
  private UUID reviewId;

  @Column(name = "review_version_id")
  private UUID reviewVersionId;

  @Column(name = "reason_code", nullable = false, length = 64)
  private String reasonCode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ReviewModerationOutcome outcome;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected ReviewModerationAuditEventJpaEntity() {
    // for JPA
  }

  ReviewModerationAuditEventJpaEntity(
      UUID id,
      UUID moderatorAccountId,
      ReviewModerationAction action,
      UUID reviewId,
      UUID reviewVersionId,
      String reasonCode,
      ReviewModerationOutcome outcome,
      Instant createdAt) {
    this.id = id;
    this.moderatorAccountId = moderatorAccountId;
    this.action = action;
    this.reviewId = reviewId;
    this.reviewVersionId = reviewVersionId;
    this.reasonCode = reasonCode;
    this.outcome = outcome;
    this.createdAt = createdAt;
  }
}
