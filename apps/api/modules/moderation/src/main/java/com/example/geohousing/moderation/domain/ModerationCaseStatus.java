package com.example.geohousing.moderation.domain;

/**
 * Where a case is in the queue. {@code CLOSED} is the only terminal status, which is what frees the
 * one-live-case-per-target slot if the content is reported again later.
 */
public enum ModerationCaseStatus {
  OPEN,
  IN_REVIEW,
  DECIDED,
  APPEALED,
  CLOSED;

  public boolean isTerminal() {
    return this == CLOSED;
  }
}
