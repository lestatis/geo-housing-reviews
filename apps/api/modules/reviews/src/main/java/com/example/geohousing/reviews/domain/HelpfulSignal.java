package com.example.geohousing.reviews.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * A private, positive signal that a reader found a review helpful.
 *
 * <p>The signal is active until withdrawn. Voter eligibility is deliberately outside this entity:
 * determining publication state and whether the voter authored the review requires the owning
 * {@link Review} aggregate and authorization context. Public review representations must expose
 * only an aggregate count, never this signal's voter or timestamps.
 */
public final class HelpfulSignal {

  private final HelpfulSignalId id;
  private final ReviewId reviewId;
  private final HelpfulSignalVoterId voterId;
  private final Instant createdAt;
  private Instant withdrawnAt;

  private HelpfulSignal(
      HelpfulSignalId id,
      ReviewId reviewId,
      HelpfulSignalVoterId voterId,
      Instant createdAt,
      Instant withdrawnAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.reviewId = Objects.requireNonNull(reviewId, "reviewId");
    this.voterId = Objects.requireNonNull(voterId, "voterId");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.withdrawnAt = withdrawnAt;
    if (withdrawnAt != null && withdrawnAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("a helpful signal cannot be withdrawn before it exists");
    }
  }

  public static HelpfulSignal create(
      HelpfulSignalId id, ReviewId reviewId, HelpfulSignalVoterId voterId, Clock clock) {
    return new HelpfulSignal(
        id, reviewId, voterId, Objects.requireNonNull(clock, "clock").instant(), null);
  }

  /** Rebuilds a signal from persisted state. Intended for persistence adapters only. */
  public static HelpfulSignal reconstitute(
      HelpfulSignalId id,
      ReviewId reviewId,
      HelpfulSignalVoterId voterId,
      Instant createdAt,
      Instant withdrawnAt) {
    return new HelpfulSignal(id, reviewId, voterId, createdAt, withdrawnAt);
  }

  /** Withdraws an active signal while preserving its history. */
  public void withdraw(Clock clock) {
    if (!isActive()) {
      throw new IllegalStateException("a helpful signal is already withdrawn");
    }
    Instant now = Objects.requireNonNull(clock, "clock").instant();
    if (now.isBefore(createdAt)) {
      throw new IllegalArgumentException("a helpful signal cannot be withdrawn before it exists");
    }
    withdrawnAt = now;
  }

  public boolean isActive() {
    return withdrawnAt == null;
  }

  public HelpfulSignalId id() {
    return id;
  }

  public ReviewId reviewId() {
    return reviewId;
  }

  public HelpfulSignalVoterId voterId() {
    return voterId;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant withdrawnAt() {
    return withdrawnAt;
  }
}
