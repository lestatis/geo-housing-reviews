package com.example.geohousing.reviews.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An append-only record that a moderator changed (or tried to change) a review's publication state.
 * Every action carries a reason code — MODERATION.md allows no unexplained moderation action — and
 * an applied decision records which immutable content version it judged, because a decision about
 * content is meaningless in the trail without the content it was about. The review id is recorded
 * even when no such review exists, so a failed attempt is still auditable.
 */
public final class ReviewModerationAuditEvent {

  private final UUID id;
  private final ModeratorId moderatorId;
  private final ReviewModerationAction action;
  private final ReviewId reviewId;
  private final UUID reviewVersionId;
  private final String reasonCode;
  private final ReviewModerationOutcome outcome;
  private final Instant occurredAt;

  private ReviewModerationAuditEvent(
      UUID id,
      ModeratorId moderatorId,
      ReviewModerationAction action,
      ReviewId reviewId,
      UUID reviewVersionId,
      String reasonCode,
      ReviewModerationOutcome outcome,
      Instant occurredAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.moderatorId = Objects.requireNonNull(moderatorId, "moderatorId");
    this.action = Objects.requireNonNull(action, "action");
    this.reviewId = Objects.requireNonNull(reviewId, "reviewId");
    this.reviewVersionId = reviewVersionId;
    this.reasonCode = requireReasonCode(reasonCode);
    this.outcome = Objects.requireNonNull(outcome, "outcome");
    this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
  }

  /**
   * Records an action that took effect. {@code reviewVersionId} is the content version the decision
   * applied to — absent only when the review had no content yet (removing an empty draft).
   */
  public static ReviewModerationAuditEvent applied(
      ModeratorId moderatorId,
      ReviewModerationAction action,
      ReviewId reviewId,
      UUID reviewVersionId,
      String reasonCode,
      Instant occurredAt) {
    return new ReviewModerationAuditEvent(
        UUID.randomUUID(),
        moderatorId,
        action,
        reviewId,
        reviewVersionId,
        reasonCode,
        ReviewModerationOutcome.APPLIED,
        occurredAt);
  }

  /** Records an action attempted against a review that does not exist. */
  public static ReviewModerationAuditEvent notFound(
      ModeratorId moderatorId,
      ReviewModerationAction action,
      ReviewId reviewId,
      String reasonCode,
      Instant occurredAt) {
    return new ReviewModerationAuditEvent(
        UUID.randomUUID(),
        moderatorId,
        action,
        reviewId,
        null,
        reasonCode,
        ReviewModerationOutcome.NOT_FOUND,
        occurredAt);
  }

  private static String requireReasonCode(String reasonCode) {
    if (reasonCode == null || reasonCode.isBlank()) {
      throw new IllegalArgumentException("every moderation action requires a reason code");
    }
    String trimmed = reasonCode.trim();
    if (trimmed.length() > 64) {
      throw new IllegalArgumentException("reasonCode must not exceed 64 characters");
    }
    return trimmed;
  }

  public UUID id() {
    return id;
  }

  public ModeratorId moderatorId() {
    return moderatorId;
  }

  public ReviewModerationAction action() {
    return action;
  }

  public ReviewId reviewId() {
    return reviewId;
  }

  public Optional<UUID> reviewVersionId() {
    return Optional.ofNullable(reviewVersionId);
  }

  public String reasonCode() {
    return reasonCode;
  }

  public ReviewModerationOutcome outcome() {
    return outcome;
  }

  public Instant occurredAt() {
    return occurredAt;
  }
}
