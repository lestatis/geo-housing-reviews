package com.example.geohousing.reviews.domain;

/**
 * Projection of the verification module's decision about the author's claimed relationship
 * (TRUST_VERIFICATION.md tiers 0–2). Never a bare "verified": it records how the relationship was
 * evidenced, and says nothing about whether the review's statements are true. Tier 0 ({@code
 * UNVERIFIED}) content still publishes.
 */
public enum VerificationTier {
  UNVERIFIED,
  RELATIONSHIP_SIGNAL,
  DOCUMENT_VERIFIED
}
