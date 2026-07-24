package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.VerificationCase;
import com.example.geohousing.verification.domain.VerificationDecisionAuditEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Expires approved badges whose validity has lapsed. A policy-driven, system-initiated sweep: it
 * takes no moderator, so the audit rows it writes carry no actor — the one case {@code
 * verification_decision_audit_event} allows that (TRUST_VERIFICATION.md §8).
 *
 * <p>Expiry reverts the tier to {@code UNVERIFIED} and projects that onto the account's review. The
 * review is not deleted and the case is not erased: the platform simply stops asserting a
 * relationship it can no longer stand behind.
 *
 * <p><strong>Narrower than §8 on purpose:</strong> §8 permits a lapsed current-resident badge to
 * <em>become</em> "verified former resident" instead of expiring. That is not done here — it would
 * rewrite the claim the account actually made, and at Tier 1 every claim carries the same
 * relationship-signal badge anyway, so the distinction only bites once Tier 2 exists. Recorded as a
 * follow-up rather than silently diverging.
 */
public final class VerificationExpiryService {

  /** Reason code stamped on a system expiry; the taxonomy belongs to the moderation policy. */
  static final String EXPIRY_REASON_CODE = "POLICY_EXPIRY";

  private final VerificationCaseRepository caseRepository;
  private final VerificationDecisionRepository decisionRepository;
  private final ReviewProjection reviewProjection;
  private final Clock clock;

  public VerificationExpiryService(
      VerificationCaseRepository caseRepository,
      VerificationDecisionRepository decisionRepository,
      ReviewProjection reviewProjection,
      Clock clock) {
    this.caseRepository = Objects.requireNonNull(caseRepository, "caseRepository");
    this.decisionRepository = Objects.requireNonNull(decisionRepository, "decisionRepository");
    this.reviewProjection = Objects.requireNonNull(reviewProjection, "reviewProjection");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Expires up to {@code limit} lapsed badges and returns how many were expired. Idempotent: a case
   * expired by an earlier run is no longer approved, so it is not selected again.
   */
  public int expireLapsed(int limit) {
    Instant now = clock.instant();
    List<VerificationCase> lapsed = caseRepository.findLapsedApproved(now, limit);

    int expired = 0;
    for (VerificationCase verificationCase : lapsed) {
      verificationCase.expire(clock);
      decisionRepository.applyDecision(
          verificationCase,
          VerificationDecisionAuditEvent.systemExpiry(
              verificationCase.id(), EXPIRY_REASON_CODE, now));
      reviewProjection.applyTier(
          verificationCase.accountRef(), verificationCase.propertyRef(), verificationCase.tier());
      expired++;
    }
    return expired;
  }
}
