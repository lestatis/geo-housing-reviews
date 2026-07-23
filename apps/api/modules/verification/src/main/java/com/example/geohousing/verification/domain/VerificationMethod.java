package com.example.geohousing.verification.domain;

/**
 * How a relationship is evidenced. Each method carries the {@link VerificationTier} it can grant,
 * so the strength of an approval is a property of the method rather than a moderator's free choice.
 *
 * <p>Only the Tier 1 relationship-signal methods exist today (mirrors the {@code method} check
 * constraint on {@code verification.verification_case}). The Tier 2 {@code DOCUMENT} method arrives
 * with the evidence subsystem in plan 007.
 */
public enum VerificationMethod {
  /** Invitation from an already-verified resident of the property. */
  INVITATION(VerificationTier.RELATIONSHIP_SIGNAL),
  /** A building-specific one-time code distributed through a trusted channel. */
  BUILDING_CODE(VerificationTier.RELATIONSHIP_SIGNAL),
  /** Repeated coarse location presence with explicit consent. */
  LOCATION_SIGNAL(VerificationTier.RELATIONSHIP_SIGNAL);

  private final VerificationTier grantedTier;

  VerificationMethod(VerificationTier grantedTier) {
    this.grantedTier = grantedTier;
  }

  /** The tier an approval by this method grants. */
  public VerificationTier grantedTier() {
    return grantedTier;
  }
}
