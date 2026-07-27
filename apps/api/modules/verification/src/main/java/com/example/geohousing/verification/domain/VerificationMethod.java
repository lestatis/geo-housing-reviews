package com.example.geohousing.verification.domain;

/**
 * How a relationship is evidenced. Each method carries the {@link VerificationTier} it can grant,
 * so the strength of an approval is a property of the method rather than a moderator's free choice.
 *
 * <p>Mirrors the {@code method} check constraint on {@code verification.verification_case}. The
 * three Tier 1 methods are signal-based and grant a relationship signal; {@code DOCUMENT} (Tier 2,
 * added in plan 007) is the one method backed by uploaded evidence and grants the stronger
 * document-verified tier.
 */
public enum VerificationMethod {
  /** Invitation from an already-verified resident of the property. */
  INVITATION(VerificationTier.RELATIONSHIP_SIGNAL, false),
  /** A building-specific one-time code distributed through a trusted channel. */
  BUILDING_CODE(VerificationTier.RELATIONSHIP_SIGNAL, false),
  /** Repeated coarse location presence with explicit consent. */
  LOCATION_SIGNAL(VerificationTier.RELATIONSHIP_SIGNAL, false),
  /** A document (lease fragment, utility bill, ownership extract) read by a moderator. */
  DOCUMENT(VerificationTier.DOCUMENT_VERIFIED, true);

  private final VerificationTier grantedTier;
  private final boolean requiresEvidence;

  VerificationMethod(VerificationTier grantedTier, boolean requiresEvidence) {
    this.grantedTier = grantedTier;
    this.requiresEvidence = requiresEvidence;
  }

  /** The tier an approval by this method grants. */
  public VerificationTier grantedTier() {
    return grantedTier;
  }

  /** Whether a case using this method is backed by uploaded evidence a moderator reads. */
  public boolean requiresEvidence() {
    return requiresEvidence;
  }
}
