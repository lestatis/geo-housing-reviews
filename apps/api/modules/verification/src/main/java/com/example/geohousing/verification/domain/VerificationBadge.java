package com.example.geohousing.verification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The public-safe projection of an approved verification case (docs/DOMAIN_MODEL.md
 * VerificationBadge; docs/TRUST_VERIFICATION.md §4). It carries only what a reader may see — a
 * label, when it was verified, until when it is valid, and the key of the mandatory explanation
 * tooltip. It never carries a document number, apartment number or exact private address.
 *
 * <p>The explanation is referenced by key rather than text so the copy can be localised by the UI;
 * the key names the tooltip that says the platform checked evidence of the reviewer's relationship,
 * not the truth of their statements.
 */
public record VerificationBadge(
    VerificationBadgeType type, Instant verifiedAt, Instant validThrough, String explanationKey) {

  /** The single explanation key; the tooltip copy lives in the UI/i18n layer. */
  public static final String EXPLANATION_KEY = "verification.badge.evidence_not_truth";

  public VerificationBadge {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(verifiedAt, "verifiedAt");
    Objects.requireNonNull(explanationKey, "explanationKey");
  }

  /**
   * Derives the badge a decision grants, or empty when the tier grants no badge. The label depends
   * on the tier: a relationship signal is always {@link
   * VerificationBadgeType#RELATIONSHIP_SIGNAL_CONFIRMED}, never a claim-specific "verified …"
   * label.
   */
  public static Optional<VerificationBadge> forDecision(
      VerificationTier tier, RelationshipClaim claim, Instant verifiedAt, Instant validThrough) {
    Objects.requireNonNull(claim, "claim");
    return badgeType(tier, claim)
        .map(type -> new VerificationBadge(type, verifiedAt, validThrough, EXPLANATION_KEY));
  }

  private static Optional<VerificationBadgeType> badgeType(
      VerificationTier tier, RelationshipClaim claim) {
    return switch (tier) {
      case UNVERIFIED -> Optional.empty();
      case RELATIONSHIP_SIGNAL -> Optional.of(VerificationBadgeType.RELATIONSHIP_SIGNAL_CONFIRMED);
      case DOCUMENT_VERIFIED ->
          Optional.of(
              switch (claim) {
                case CURRENT_RESIDENT -> VerificationBadgeType.VERIFIED_CURRENT_TENANT;
                case FORMER_RESIDENT -> VerificationBadgeType.VERIFIED_FORMER_TENANT;
                case OWNER -> VerificationBadgeType.VERIFIED_OWNER;
                case FORMER_OWNER -> VerificationBadgeType.VERIFIED_FORMER_OWNER;
              });
    };
  }

  /** When the badge stops being valid, if it expires at all. */
  public Optional<Instant> expiry() {
    return Optional.ofNullable(validThrough);
  }
}
