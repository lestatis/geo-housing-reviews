package com.example.geohousing.reviews.domain;

/**
 * The moderation actions that change a review's publication state. Mirrors the {@code action} check
 * constraint on {@code reviews.review_moderation_audit_event}. Richer decision actions (redaction,
 * requested changes, account restriction, escalation) belong to the moderation module.
 */
public enum ReviewModerationAction {
  PUBLISH,
  REJECT,
  HIDE,
  RESTORE,
  REMOVE
}
