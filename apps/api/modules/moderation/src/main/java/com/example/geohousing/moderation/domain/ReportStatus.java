package com.example.geohousing.moderation.domain;

/**
 * A report's own lifecycle, separate from the case it feeds: {@code OPEN} on arrival, {@code
 * LINKED} once attached to the case it is evidence for, then {@code RESOLVED} or {@code DISMISSED}.
 *
 * <p>The distinction matters for the one-live-report-per-account rule: while a report is OPEN or
 * LINKED the same account cannot file another about the same content, but once it reaches a
 * terminal state a genuinely new problem deserves to be heard.
 */
public enum ReportStatus {
  OPEN,
  LINKED,
  RESOLVED,
  DISMISSED;

  /** Whether this report still occupies its reporter's one slot for this target. */
  public boolean isLive() {
    return this == OPEN || this == LINKED;
  }
}
