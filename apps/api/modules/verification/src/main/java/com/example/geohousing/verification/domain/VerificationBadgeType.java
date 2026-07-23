package com.example.geohousing.verification.domain;

/**
 * A public-safe badge label (docs/TRUST_VERIFICATION.md §4).
 *
 * <p>The label depends on the <em>tier</em>, not only the claim. A Tier 1 relationship signal is
 * deliberately not labelled "verified resident" — §2 requires the narrower {@link
 * #RELATIONSHIP_SIGNAL_CONFIRMED}. Only Tier 2 document verification earns a claim-specific
 * "verified …" badge, so those four types are reachable once the evidence subsystem exists.
 */
public enum VerificationBadgeType {
  RELATIONSHIP_SIGNAL_CONFIRMED,
  VERIFIED_CURRENT_TENANT,
  VERIFIED_FORMER_TENANT,
  VERIFIED_OWNER,
  VERIFIED_FORMER_OWNER
}
