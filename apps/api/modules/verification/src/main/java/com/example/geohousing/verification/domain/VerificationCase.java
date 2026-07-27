package com.example.geohousing.verification.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A private workflow linking an account, a property, a claimed relationship and a method, ending in
 * a decision that grants a strength {@link VerificationTier} and a public {@link VerificationBadge}
 * (docs/DOMAIN_MODEL.md VerificationCase; docs/TRUST_VERIFICATION.md).
 *
 * <p>State machine: {@code PENDING → APPROVED} (a moderator approves) or {@code REJECTED} (a
 * moderator rejects), or {@code CANCELLED} (the account withdraws before a decision). An approved
 * case may later be {@code EXPIRED} (a policy-driven, system-initiated lapse) or revoked back to
 * {@code REJECTED} (forged evidence, a compromised account, moderator error). The three terminal
 * states free the one-live-case slot so the account can try again.
 *
 * <p>The tier is granted by the method, not chosen by the moderator: an approval is as strong as
 * the evidence method allows and no stronger (§2). The tier is {@code UNVERIFIED} until an approval
 * and reverts to it on revocation or expiry — revocation never deletes anything; the review it fed
 * simply falls back to unverified (§8).
 */
public final class VerificationCase {

  private final VerificationCaseId id;
  private final AccountRef accountRef;
  private final PropertyRef propertyRef;
  private final RelationshipClaim relationshipClaim;
  private final VerificationMethod method;
  private VerificationStatus status;
  private VerificationTier tier;
  private String decisionReasonCode;
  private final int policyVersion;
  private Instant verifiedAt;
  private Instant validThrough;
  private ModeratorId decidedBy;
  private final Instant createdAt;
  private Instant updatedAt;
  private final long version;

  private VerificationCase(
      VerificationCaseId id,
      AccountRef accountRef,
      PropertyRef propertyRef,
      RelationshipClaim relationshipClaim,
      VerificationMethod method,
      VerificationStatus status,
      VerificationTier tier,
      String decisionReasonCode,
      int policyVersion,
      Instant verifiedAt,
      Instant validThrough,
      ModeratorId decidedBy,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    this.id = Objects.requireNonNull(id, "id");
    this.accountRef = Objects.requireNonNull(accountRef, "accountRef");
    this.propertyRef = Objects.requireNonNull(propertyRef, "propertyRef");
    this.relationshipClaim = Objects.requireNonNull(relationshipClaim, "relationshipClaim");
    this.method = Objects.requireNonNull(method, "method");
    this.status = Objects.requireNonNull(status, "status");
    this.tier = Objects.requireNonNull(tier, "tier");
    this.decisionReasonCode = decisionReasonCode;
    this.policyVersion = policyVersion;
    this.verifiedAt = verifiedAt;
    this.validThrough = validThrough;
    this.decidedBy = decidedBy;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    this.version = version;
    checkInvariants();
  }

  /** Opens a new {@code PENDING} case for a moderator to decide. */
  public static VerificationCase open(
      VerificationCaseId id,
      AccountRef accountRef,
      PropertyRef propertyRef,
      RelationshipClaim relationshipClaim,
      VerificationMethod method,
      int policyVersion,
      Clock clock) {
    Objects.requireNonNull(clock, "clock");
    Instant now = clock.instant();
    return new VerificationCase(
        id,
        accountRef,
        propertyRef,
        relationshipClaim,
        method,
        VerificationStatus.PENDING,
        VerificationTier.UNVERIFIED,
        null,
        policyVersion,
        null,
        null,
        null,
        now,
        now,
        0L);
  }

  /** Rebuilds a case from persisted state. Intended for persistence adapters only. */
  public static VerificationCase reconstitute(
      VerificationCaseId id,
      AccountRef accountRef,
      PropertyRef propertyRef,
      RelationshipClaim relationshipClaim,
      VerificationMethod method,
      VerificationStatus status,
      VerificationTier tier,
      String decisionReasonCode,
      int policyVersion,
      Instant verifiedAt,
      Instant validThrough,
      ModeratorId decidedBy,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    return new VerificationCase(
        id,
        accountRef,
        propertyRef,
        relationshipClaim,
        method,
        status,
        tier,
        decisionReasonCode,
        policyVersion,
        verifiedAt,
        validThrough,
        decidedBy,
        createdAt,
        updatedAt,
        version);
  }

  /**
   * Approves a pending case, granting the tier the method allows. {@code validThrough} is optional
   * — a current-resident badge may be given an expiry (§8); pass {@code null} for none.
   */
  public void approve(
      ModeratorId moderatorId, String reasonCode, Instant validThrough, Clock clock) {
    // Validate everything before touching any state: a rejected reason must leave the case
    // exactly PENDING, not half-transitioned.
    requirePending("approved");
    Objects.requireNonNull(moderatorId, "moderatorId");
    String reason = requireReason(reasonCode);
    this.status = VerificationStatus.APPROVED;
    this.tier = method.grantedTier();
    this.decidedBy = moderatorId;
    this.decisionReasonCode = reason;
    this.verifiedAt = clock.instant();
    this.validThrough = validThrough;
    touch(clock);
  }

  /** Rejects a pending case; no tier is granted. */
  public void reject(ModeratorId moderatorId, String reasonCode, Clock clock) {
    requirePending("rejected");
    Objects.requireNonNull(moderatorId, "moderatorId");
    String reason = requireReason(reasonCode);
    this.status = VerificationStatus.REJECTED;
    this.tier = VerificationTier.UNVERIFIED;
    this.decidedBy = moderatorId;
    this.decisionReasonCode = reason;
    touch(clock);
  }

  /** Withdraws a pending case at the account's own request; no decision is recorded. */
  public void cancel(Clock clock) {
    requirePending("cancelled");
    this.status = VerificationStatus.CANCELLED;
    this.tier = VerificationTier.UNVERIFIED;
    touch(clock);
  }

  /**
   * Revokes an approved badge (forged evidence, compromised account, moderator error). The case
   * becomes {@code REJECTED} and the tier reverts to {@code UNVERIFIED}; the review it fed falls
   * back to unverified rather than being deleted (§8).
   */
  public void revoke(ModeratorId moderatorId, String reasonCode, Clock clock) {
    if (status != VerificationStatus.APPROVED) {
      throw new IllegalVerificationStateTransitionException(
          "only an APPROVED case can be revoked, was " + status);
    }
    Objects.requireNonNull(moderatorId, "moderatorId");
    String reason = requireReason(reasonCode);
    this.status = VerificationStatus.REJECTED;
    this.tier = VerificationTier.UNVERIFIED;
    this.decidedBy = moderatorId;
    this.decisionReasonCode = reason;
    this.validThrough = null;
    touch(clock);
  }

  /**
   * Expires an approved badge whose validity has lapsed — a policy-driven, system-initiated
   * transition, so it takes no moderator. The tier reverts to {@code UNVERIFIED}.
   */
  public void expire(Clock clock) {
    if (status != VerificationStatus.APPROVED) {
      throw new IllegalVerificationStateTransitionException(
          "only an APPROVED case can expire, was " + status);
    }
    this.status = VerificationStatus.EXPIRED;
    this.tier = VerificationTier.UNVERIFIED;
    touch(clock);
  }

  /**
   * Whether evidence may be attached now: only a pending, document-method case takes uploads. A
   * signal-based case is judged from the signal, not a document, and a decided case is closed to
   * new evidence.
   */
  public boolean acceptsEvidence() {
    return status == VerificationStatus.PENDING && method.requiresEvidence();
  }

  /** The public badge this case grants, or empty unless it is an approved, tier-bearing case. */
  public Optional<VerificationBadge> badge() {
    if (status != VerificationStatus.APPROVED) {
      return Optional.empty();
    }
    return VerificationBadge.forDecision(tier, relationshipClaim, verifiedAt, validThrough);
  }

  private void requirePending(String action) {
    if (status != VerificationStatus.PENDING) {
      throw new IllegalVerificationStateTransitionException(
          "only a PENDING case can be " + action + ", was " + status);
    }
  }

  private static String requireReason(String reasonCode) {
    if (reasonCode == null || reasonCode.isBlank()) {
      throw new IllegalArgumentException("a verification decision requires a reason code");
    }
    return reasonCode.trim();
  }

  private void checkInvariants() {
    if (status == VerificationStatus.APPROVED && verifiedAt == null) {
      throw new IllegalArgumentException("an approved case must record when it was verified");
    }
    if ((status == VerificationStatus.APPROVED || status == VerificationStatus.REJECTED)
        && (decidedBy == null || decisionReasonCode == null)) {
      throw new IllegalArgumentException("a decided case must record its decider and reason");
    }
    if (status == VerificationStatus.APPROVED && tier == VerificationTier.UNVERIFIED) {
      throw new IllegalArgumentException("an approved case must grant a tier");
    }
  }

  private void touch(Clock clock) {
    this.updatedAt = Objects.requireNonNull(clock, "clock").instant();
  }

  public VerificationCaseId id() {
    return id;
  }

  public AccountRef accountRef() {
    return accountRef;
  }

  public PropertyRef propertyRef() {
    return propertyRef;
  }

  public RelationshipClaim relationshipClaim() {
    return relationshipClaim;
  }

  public VerificationMethod method() {
    return method;
  }

  public VerificationStatus status() {
    return status;
  }

  public VerificationTier tier() {
    return tier;
  }

  public Optional<String> decisionReasonCode() {
    return Optional.ofNullable(decisionReasonCode);
  }

  public int policyVersion() {
    return policyVersion;
  }

  public Optional<Instant> verifiedAt() {
    return Optional.ofNullable(verifiedAt);
  }

  public Optional<Instant> validThrough() {
    return Optional.ofNullable(validThrough);
  }

  public Optional<ModeratorId> decidedBy() {
    return Optional.ofNullable(decidedBy);
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public long version() {
    return version;
  }
}
