package com.example.geohousing.verification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class VerificationBadgeTest {

  private static final Instant VERIFIED_AT = Instant.parse("2026-07-23T10:00:00Z");

  @Test
  void everyTier1MethodGrantsTheRelationshipSignalTier() {
    for (VerificationMethod method : VerificationMethod.values()) {
      assertThat(method.grantedTier()).isEqualTo(VerificationTier.RELATIONSHIP_SIGNAL);
    }
  }

  @Test
  void anUnverifiedTierYieldsNoBadge() {
    assertThat(
            VerificationBadge.forDecision(
                VerificationTier.UNVERIFIED, RelationshipClaim.OWNER, VERIFIED_AT, null))
        .isEmpty();
  }

  @Test
  void aRelationshipSignalIsTheSameCautiousBadgeWhateverTheClaim() {
    for (RelationshipClaim claim : RelationshipClaim.values()) {
      VerificationBadge badge =
          VerificationBadge.forDecision(
                  VerificationTier.RELATIONSHIP_SIGNAL, claim, VERIFIED_AT, null)
              .orElseThrow();
      assertThat(badge.type()).isEqualTo(VerificationBadgeType.RELATIONSHIP_SIGNAL_CONFIRMED);
    }
  }

  @Test
  void documentVerificationEarnsAClaimSpecificVerifiedBadge() {
    assertThat(badgeType(VerificationTier.DOCUMENT_VERIFIED, RelationshipClaim.CURRENT_RESIDENT))
        .isEqualTo(VerificationBadgeType.VERIFIED_CURRENT_TENANT);
    assertThat(badgeType(VerificationTier.DOCUMENT_VERIFIED, RelationshipClaim.FORMER_RESIDENT))
        .isEqualTo(VerificationBadgeType.VERIFIED_FORMER_TENANT);
    assertThat(badgeType(VerificationTier.DOCUMENT_VERIFIED, RelationshipClaim.OWNER))
        .isEqualTo(VerificationBadgeType.VERIFIED_OWNER);
    assertThat(badgeType(VerificationTier.DOCUMENT_VERIFIED, RelationshipClaim.FORMER_OWNER))
        .isEqualTo(VerificationBadgeType.VERIFIED_FORMER_OWNER);
  }

  private static VerificationBadgeType badgeType(VerificationTier tier, RelationshipClaim claim) {
    return VerificationBadge.forDecision(tier, claim, VERIFIED_AT, null).orElseThrow().type();
  }
}
