package com.example.geohousing.reviews.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Persistence row for one private helpful signal. Voter identity is never part of public reads. */
@Entity
@Table(schema = "reviews", name = "review_helpful_signal")
class HelpfulSignalJpaEntity {

  @Id private UUID id;

  @Column(name = "review_id", nullable = false)
  private UUID reviewId;

  @Column(name = "voter_account_id", nullable = false)
  private UUID voterAccountId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "withdrawn_at")
  private Instant withdrawnAt;

  protected HelpfulSignalJpaEntity() {
    // for JPA
  }

  HelpfulSignalJpaEntity(
      UUID id, UUID reviewId, UUID voterAccountId, Instant createdAt, Instant withdrawnAt) {
    this.id = id;
    this.reviewId = reviewId;
    this.voterAccountId = voterAccountId;
    this.createdAt = createdAt;
    this.withdrawnAt = withdrawnAt;
  }

  UUID id() {
    return id;
  }

  UUID reviewId() {
    return reviewId;
  }

  UUID voterAccountId() {
    return voterAccountId;
  }

  Instant createdAt() {
    return createdAt;
  }

  Instant withdrawnAt() {
    return withdrawnAt;
  }

  void withdraw(Instant at) {
    if (withdrawnAt == null) {
      withdrawnAt = at;
    }
  }
}
