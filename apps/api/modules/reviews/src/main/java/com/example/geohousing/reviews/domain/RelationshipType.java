package com.example.geohousing.reviews.domain;

/**
 * The relationship the author claims to the property. Mirrors the {@code relationship_type} check
 * constraint on {@code reviews.review}; {@code OTHER} exists so nobody is forced into a false
 * claim.
 */
public enum RelationshipType {
  CURRENT_RESIDENT,
  FORMER_RESIDENT,
  OWNER,
  OTHER
}
