package com.example.geohousing.reviews.domain;

/**
 * Publication lifecycle of a review. {@code REJECTED} and {@code REMOVED} are terminal — the author
 * starts a fresh review instead (the one-live-review index excludes terminal states). Mirrors the
 * {@code status} check constraint on {@code reviews.review}.
 */
public enum ReviewStatus {
  DRAFT,
  PENDING_MODERATION,
  PUBLISHED,
  REJECTED,
  HIDDEN,
  REMOVED
}
