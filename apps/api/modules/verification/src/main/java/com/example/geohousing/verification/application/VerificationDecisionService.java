package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.ModeratorId;
import com.example.geohousing.verification.domain.VerificationCase;
import com.example.geohousing.verification.domain.VerificationCaseId;
import com.example.geohousing.verification.domain.VerificationDecisionAction;
import com.example.geohousing.verification.domain.VerificationDecisionAuditEvent;
import com.example.geohousing.verification.domain.VerificationVersionConflictException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Applies moderator decisions to verification cases. Every decision — including one against a case
 * that does not exist — is audited with its reason code; the mutation and the audit row are
 * committed together by the {@link VerificationDecisionRepository}.
 *
 * <p>{@code expectedVersion} is the version the moderator saw. A mismatch is refused before
 * anything happens, so a moderator never decides on a case that changed under them.
 *
 * <p>This service owns approving and rejecting pending cases. Revocation and expiry of already
 * approved badges are a later increment; the reason-code taxonomy belongs to the moderation policy.
 */
public final class VerificationDecisionService {

  private final VerificationCaseRepository caseRepository;
  private final VerificationDecisionRepository decisionRepository;
  private final ReviewProjection reviewProjection;
  private final Clock clock;

  public VerificationDecisionService(
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
   * Approves a pending case, granting the tier its method allows. {@code validThrough} is optional.
   */
  public Optional<VerificationCase> approve(
      ModeratorId moderatorId,
      VerificationCaseId caseId,
      long expectedVersion,
      String reasonCode,
      Instant validThrough) {
    return decide(
        moderatorId,
        caseId,
        expectedVersion,
        reasonCode,
        VerificationDecisionAction.APPROVE,
        (verificationCase, now) ->
            verificationCase.approve(moderatorId, reasonCode, validThrough, clock));
  }

  /** Rejects a pending case; no tier is granted. */
  public Optional<VerificationCase> reject(
      ModeratorId moderatorId, VerificationCaseId caseId, long expectedVersion, String reasonCode) {
    return decide(
        moderatorId,
        caseId,
        expectedVersion,
        reasonCode,
        VerificationDecisionAction.REJECT,
        (verificationCase, now) -> verificationCase.reject(moderatorId, reasonCode, clock));
  }

  /**
   * Revokes an approved badge — forged evidence, a compromised account, a moderator's error. The
   * case becomes terminal and the tier reverts to {@code UNVERIFIED}, which is projected onto the
   * account's review: the review itself is never deleted, it simply stops carrying the badge
   * (TRUST_VERIFICATION.md §8).
   */
  public Optional<VerificationCase> revoke(
      ModeratorId moderatorId, VerificationCaseId caseId, long expectedVersion, String reasonCode) {
    return decide(
        moderatorId,
        caseId,
        expectedVersion,
        reasonCode,
        VerificationDecisionAction.REVOKE,
        (verificationCase, now) -> verificationCase.revoke(moderatorId, reasonCode, clock));
  }

  private Optional<VerificationCase> decide(
      ModeratorId moderatorId,
      VerificationCaseId caseId,
      long expectedVersion,
      String reasonCode,
      VerificationDecisionAction action,
      Decision decision) {
    Objects.requireNonNull(moderatorId, "moderatorId");
    Objects.requireNonNull(caseId, "caseId");
    if (reasonCode == null || reasonCode.isBlank()) {
      // Checked before anything else happens (the audit event and aggregate enforce it too): a
      // decision without a reason must fail before any state is touched.
      throw new IllegalArgumentException("a verification decision requires a reason code");
    }
    Instant now = clock.instant();

    Optional<VerificationCase> found = caseRepository.findById(caseId);
    if (found.isEmpty()) {
      decisionRepository.recordAttempt(
          VerificationDecisionAuditEvent.notFound(
              moderatorId.value(), action, caseId, reasonCode, now));
      return Optional.empty();
    }

    VerificationCase verificationCase = found.get();
    if (verificationCase.version() != expectedVersion) {
      throw new VerificationVersionConflictException(
          "verification case " + caseId.value() + " changed since the moderator loaded it");
    }
    decision.apply(verificationCase, now);
    decisionRepository.applyDecision(
        verificationCase,
        VerificationDecisionAuditEvent.applied(
            moderatorId.value(), action, caseId, reasonCode, now));

    // Project the resulting tier onto the account's review. Uniform across decisions: approval
    // raises it, rejection leaves it UNVERIFIED (a pending case never granted a tier, and the
    // one-live-case rule means no other approval coexists). The push is a separate concern from the
    // decision's own transaction — it is idempotent, so a later re-push reconciles if it fails
    // here.
    reviewProjection.applyTier(
        verificationCase.accountRef(), verificationCase.propertyRef(), verificationCase.tier());
    return Optional.of(verificationCase);
  }

  @FunctionalInterface
  private interface Decision {
    void apply(VerificationCase verificationCase, Instant now);
  }
}
