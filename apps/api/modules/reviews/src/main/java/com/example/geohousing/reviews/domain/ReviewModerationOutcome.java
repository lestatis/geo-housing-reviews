package com.example.geohousing.reviews.domain;

/**
 * Whether a moderation action took effect or was attempted against a review that does not exist.
 */
public enum ReviewModerationOutcome {
  APPLIED,
  NOT_FOUND
}
