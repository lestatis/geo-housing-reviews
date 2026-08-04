package com.example.geohousing.verification.api;

import java.time.Instant;

/**
 * What verification can say about its queue and its decisions.
 *
 * <p>Counts only, and nothing derived from evidence. {@code SECURITY_PRIVACY.md} and ADR-0008 both
 * forbid raw evidence reaching analytics; a count of decisions touches none of it, and nothing here
 * should ever start needing to.
 */
public interface VerificationMetrics {

  /** Cases waiting on a moderator. */
  long pendingCases();

  /** Decisions recorded in a window, {@code from} inclusive and {@code until} exclusive. */
  VerificationThroughput between(Instant from, Instant until);
}
