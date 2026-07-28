package com.example.geohousing.moderation.domain;

/**
 * How much care a case needs. {@code HIGH} marks content with real-world consequences (allegations
 * about identifiable people, privacy exposure); {@code LEGAL} marks a case that has left ordinary
 * moderation for legal review.
 *
 * <p>This is a routing hint for humans, not an automated judgement — nothing in this module decides
 * an outcome from the risk level.
 */
public enum RiskLevel {
  STANDARD,
  HIGH,
  LEGAL
}
