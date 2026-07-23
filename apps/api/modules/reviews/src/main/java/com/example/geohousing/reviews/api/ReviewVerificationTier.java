package com.example.geohousing.reviews.api;

/**
 * The verification strength another module may project onto a review (docs/TRUST_VERIFICATION.md
 * §2). A published-contract type, deliberately separate from the reviews domain enum so the wire
 * contract does not expose internal types — the same discipline as {@code PropertyVisibility} in
 * the properties api.
 *
 * <p>Never a bare "verified": the tier records how a relationship was evidenced, and says nothing
 * about whether the review's statements are true.
 */
public enum ReviewVerificationTier {
  UNVERIFIED,
  RELATIONSHIP_SIGNAL,
  DOCUMENT_VERIFIED
}
