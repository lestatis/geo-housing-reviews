package com.example.geohousing.verification.domain;

/**
 * Strength of a verification decision (docs/TRUST_VERIFICATION.md §2). Never a bare {@code
 * verified=true}: the tier records <em>how</em> the relationship was evidenced.
 *
 * <p>Deliberately the same set of values as the reviews module's {@code VerificationTier}, but a
 * separate type — modules do not share domain types across their boundary. The reviews projection
 * is fed the equivalent value through the published contract, not this class.
 */
public enum VerificationTier {
  /** No decision, or a decision that granted no strength. */
  UNVERIFIED,
  /** Tier 1: a relationship signal (invitation, building code, consented location). */
  RELATIONSHIP_SIGNAL,
  /** Tier 2: document-assisted verification. Reached only once the evidence subsystem exists. */
  DOCUMENT_VERIFIED
}
