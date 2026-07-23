package com.example.geohousing.verification.domain;

/**
 * The relationship an account claims to have had with a property. Broader than the reviews module's
 * relationship type on purpose: an owner verifies ownership and then reviews. Mirrors the {@code
 * relationship_claim} check constraint on {@code verification.verification_case} and drives the
 * public badge vocabulary (docs/TRUST_VERIFICATION.md §4).
 */
public enum RelationshipClaim {
  CURRENT_RESIDENT,
  FORMER_RESIDENT,
  OWNER,
  FORMER_OWNER
}
