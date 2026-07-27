package com.example.geohousing.verification.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * How long raw evidence may be kept. The deadline is computed at upload (docs/SECURITY_PRIVACY.md
 * §6: "retention deadline set at upload").
 *
 * <p>The period is <strong>configuration, never a constant</strong>: SECURITY_PRIVACY says exact
 * retention periods require legal and operational approval, which is still outstanding, so a legal
 * answer must change a setting rather than this code. The default here is a placeholder for local
 * development, not a policy decision.
 *
 * @param uploadRetention how long evidence may live from upload, as a backstop when no decision is
 *     ever made
 * @param postDecisionRetention how long it may live after a decision, covering the appeal window
 */
public record EvidenceRetentionPolicy(Duration uploadRetention, Duration postDecisionRetention) {

  public EvidenceRetentionPolicy {
    Objects.requireNonNull(uploadRetention, "uploadRetention");
    Objects.requireNonNull(postDecisionRetention, "postDecisionRetention");
    if (uploadRetention.isNegative() || uploadRetention.isZero()) {
      throw new IllegalArgumentException("uploadRetention must be positive");
    }
    if (postDecisionRetention.isNegative()) {
      throw new IllegalArgumentException("postDecisionRetention must not be negative");
    }
  }

  /** The deadline stamped on evidence when it is uploaded. */
  public Instant deadlineFromUpload(Clock clock) {
    return Objects.requireNonNull(clock, "clock").instant().plus(uploadRetention);
  }

  /**
   * The deadline once a decision has been made — normally sooner than the upload backstop, because
   * evidence should not outlive the decision plus its appeal window.
   */
  public Instant deadlineFromDecision(Clock clock) {
    return Objects.requireNonNull(clock, "clock").instant().plus(postDecisionRetention);
  }
}
