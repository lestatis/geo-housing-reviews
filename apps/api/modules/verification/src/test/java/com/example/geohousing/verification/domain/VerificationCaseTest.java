package com.example.geohousing.verification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VerificationCaseTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-23T10:00:00Z"), ZoneOffset.UTC);
  private static final Clock LATER =
      Clock.fixed(Instant.parse("2026-07-24T10:00:00Z"), ZoneOffset.UTC);
  private static final ModeratorId MODERATOR = ModeratorId.of(UUID.randomUUID());

  private static VerificationCase pending() {
    return VerificationCase.open(
        VerificationCaseId.of(UUID.randomUUID()),
        AccountRef.of(UUID.randomUUID()),
        PropertyRef.of(UUID.randomUUID()),
        RelationshipClaim.CURRENT_RESIDENT,
        VerificationMethod.INVITATION,
        1,
        CLOCK);
  }

  @Test
  void aNewCaseIsPendingAndUnverifiedWithNoBadge() {
    VerificationCase verificationCase = pending();

    assertThat(verificationCase.status()).isEqualTo(VerificationStatus.PENDING);
    assertThat(verificationCase.tier()).isEqualTo(VerificationTier.UNVERIFIED);
    assertThat(verificationCase.verifiedAt()).isEmpty();
    assertThat(verificationCase.decidedBy()).isEmpty();
    assertThat(verificationCase.badge()).isEmpty();
    assertThat(verificationCase.version()).isZero();
  }

  @Test
  void approvingGrantsTheTierTheMethodAllowsAndRecordsTheDecision() {
    VerificationCase verificationCase = pending();

    verificationCase.approve(MODERATOR, "INVITE_CONFIRMED", null, LATER);

    assertThat(verificationCase.status()).isEqualTo(VerificationStatus.APPROVED);
    assertThat(verificationCase.tier()).isEqualTo(VerificationTier.RELATIONSHIP_SIGNAL);
    assertThat(verificationCase.verifiedAt()).contains(LATER.instant());
    assertThat(verificationCase.decidedBy()).contains(MODERATOR);
    assertThat(verificationCase.decisionReasonCode()).contains("INVITE_CONFIRMED");
  }

  @Test
  void onlyAPendingDocumentCaseAcceptsEvidence() {
    VerificationCase signalCase = pending(); // INVITATION
    assertThat(signalCase.acceptsEvidence()).isFalse();

    VerificationCase documentCase =
        VerificationCase.open(
            VerificationCaseId.of(UUID.randomUUID()),
            AccountRef.of(UUID.randomUUID()),
            PropertyRef.of(UUID.randomUUID()),
            RelationshipClaim.OWNER,
            VerificationMethod.DOCUMENT,
            1,
            CLOCK);
    assertThat(documentCase.acceptsEvidence()).isTrue();

    documentCase.approve(MODERATOR, "DOC_OK", null, LATER);
    // A decided case takes no more evidence.
    assertThat(documentCase.acceptsEvidence()).isFalse();
  }

  @Test
  void approvingADocumentCaseGrantsTierTwoAndAClaimSpecificBadge() {
    VerificationCase documentCase =
        VerificationCase.open(
            VerificationCaseId.of(UUID.randomUUID()),
            AccountRef.of(UUID.randomUUID()),
            PropertyRef.of(UUID.randomUUID()),
            RelationshipClaim.OWNER,
            VerificationMethod.DOCUMENT,
            1,
            CLOCK);

    documentCase.approve(MODERATOR, "LEASE_CONFIRMED", null, LATER);

    assertThat(documentCase.tier()).isEqualTo(VerificationTier.DOCUMENT_VERIFIED);
    // Tier 2 earns the claim-specific "verified owner" badge, not the cautious signal label.
    assertThat(documentCase.badge().orElseThrow().type())
        .isEqualTo(VerificationBadgeType.VERIFIED_OWNER);
  }

  @Test
  void aTier1ApprovalIsBadgedAsARelationshipSignalNotAVerifiedResident() {
    VerificationCase verificationCase = pending(); // CURRENT_RESIDENT claim
    verificationCase.approve(MODERATOR, "CLEAN", null, LATER);

    VerificationBadge badge = verificationCase.badge().orElseThrow();
    // The cautious label: a signal is not "verified current tenant" — that is Tier 2 only.
    assertThat(badge.type()).isEqualTo(VerificationBadgeType.RELATIONSHIP_SIGNAL_CONFIRMED);
    assertThat(badge.verifiedAt()).isEqualTo(LATER.instant());
    assertThat(badge.explanationKey()).isEqualTo(VerificationBadge.EXPLANATION_KEY);
    assertThat(badge.expiry()).isEmpty();
  }

  @Test
  void anApprovalMayCarryAnExpiry() {
    VerificationCase verificationCase = pending();
    Instant validThrough = Instant.parse("2027-07-24T10:00:00Z");

    verificationCase.approve(MODERATOR, "CLEAN", validThrough, LATER);

    assertThat(verificationCase.validThrough()).contains(validThrough);
    assertThat(verificationCase.badge().orElseThrow().expiry()).contains(validThrough);
  }

  @Test
  void aReviewerCannotSkipTheReasonCode() {
    VerificationCase verificationCase = pending();
    assertThatThrownBy(() -> verificationCase.approve(MODERATOR, "  ", null, LATER))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> verificationCase.reject(MODERATOR, null, LATER))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aRejectedReasonLeavesTheCaseCleanlyPendingNotHalfDecided() {
    VerificationCase verificationCase = pending();

    assertThatThrownBy(() -> verificationCase.approve(MODERATOR, "  ", null, LATER))
        .isInstanceOf(IllegalArgumentException.class);

    // The failed approval must not have advanced the state — the case is still decidable.
    assertThat(verificationCase.status()).isEqualTo(VerificationStatus.PENDING);
    assertThat(verificationCase.decidedBy()).isEmpty();
    verificationCase.approve(MODERATOR, "CLEAN", null, LATER);
    assertThat(verificationCase.status()).isEqualTo(VerificationStatus.APPROVED);
  }

  @Test
  void rejectingGrantsNoTierAndLeavesNoBadge() {
    VerificationCase verificationCase = pending();

    verificationCase.reject(MODERATOR, "NO_EVIDENCE", LATER);

    assertThat(verificationCase.status()).isEqualTo(VerificationStatus.REJECTED);
    assertThat(verificationCase.tier()).isEqualTo(VerificationTier.UNVERIFIED);
    assertThat(verificationCase.badge()).isEmpty();
    assertThat(verificationCase.status().isTerminal()).isTrue();
  }

  @Test
  void cancellingNeedsNoModeratorAndRecordsNoDecision() {
    VerificationCase verificationCase = pending();

    verificationCase.cancel(LATER);

    assertThat(verificationCase.status()).isEqualTo(VerificationStatus.CANCELLED);
    assertThat(verificationCase.decidedBy()).isEmpty();
    assertThat(verificationCase.decisionReasonCode()).isEmpty();
  }

  @Test
  void onlyPendingCasesCanBeApprovedRejectedOrCancelled() {
    VerificationCase approved = pending();
    approved.approve(MODERATOR, "CLEAN", null, LATER);

    assertThatThrownBy(() -> approved.approve(MODERATOR, "CLEAN", null, LATER))
        .isInstanceOf(IllegalVerificationStateTransitionException.class);
    assertThatThrownBy(() -> approved.reject(MODERATOR, "CLEAN", LATER))
        .isInstanceOf(IllegalVerificationStateTransitionException.class);
    assertThatThrownBy(() -> approved.cancel(LATER))
        .isInstanceOf(IllegalVerificationStateTransitionException.class);
  }

  @Test
  void revokingAnApprovedBadgeRevertsItToUnverifiedWithoutDeletingTheCase() {
    VerificationCase verificationCase = pending();
    verificationCase.approve(MODERATOR, "CLEAN", null, LATER);

    verificationCase.revoke(MODERATOR, "FORGED_INVITE", LATER);

    assertThat(verificationCase.status()).isEqualTo(VerificationStatus.REJECTED);
    assertThat(verificationCase.tier()).isEqualTo(VerificationTier.UNVERIFIED);
    assertThat(verificationCase.badge()).isEmpty();
    assertThat(verificationCase.decisionReasonCode()).contains("FORGED_INVITE");
  }

  @Test
  void onlyAnApprovedCaseCanBeRevokedOrExpired() {
    VerificationCase stillPending = pending();
    assertThatThrownBy(() -> stillPending.revoke(MODERATOR, "x", LATER))
        .isInstanceOf(IllegalVerificationStateTransitionException.class);
    assertThatThrownBy(() -> stillPending.expire(LATER))
        .isInstanceOf(IllegalVerificationStateTransitionException.class);
  }

  @Test
  void expiryRevertsTheTierAndIsTerminal() {
    VerificationCase verificationCase = pending();
    verificationCase.approve(MODERATOR, "CLEAN", null, LATER);

    verificationCase.expire(LATER);

    assertThat(verificationCase.status()).isEqualTo(VerificationStatus.EXPIRED);
    assertThat(verificationCase.tier()).isEqualTo(VerificationTier.UNVERIFIED);
    assertThat(verificationCase.badge()).isEmpty();
    assertThat(verificationCase.status().isTerminal()).isTrue();
  }

  @Test
  void reconstituteRejectsAnApprovedCaseWithoutAVerificationTime() {
    assertThatThrownBy(
            () ->
                VerificationCase.reconstitute(
                    VerificationCaseId.of(UUID.randomUUID()),
                    AccountRef.of(UUID.randomUUID()),
                    PropertyRef.of(UUID.randomUUID()),
                    RelationshipClaim.OWNER,
                    VerificationMethod.INVITATION,
                    VerificationStatus.APPROVED,
                    VerificationTier.RELATIONSHIP_SIGNAL,
                    "CLEAN",
                    1,
                    null,
                    null,
                    MODERATOR,
                    CLOCK.instant(),
                    CLOCK.instant(),
                    0L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reconstituteRejectsAnApprovedCaseThatGrantedNoTier() {
    assertThatThrownBy(
            () ->
                VerificationCase.reconstitute(
                    VerificationCaseId.of(UUID.randomUUID()),
                    AccountRef.of(UUID.randomUUID()),
                    PropertyRef.of(UUID.randomUUID()),
                    RelationshipClaim.OWNER,
                    VerificationMethod.INVITATION,
                    VerificationStatus.APPROVED,
                    VerificationTier.UNVERIFIED,
                    "CLEAN",
                    1,
                    CLOCK.instant(),
                    null,
                    MODERATOR,
                    CLOCK.instant(),
                    CLOCK.instant(),
                    0L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
