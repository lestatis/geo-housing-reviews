package com.example.geohousing.properties.domain;

/**
 * Lifecycle status of a property. {@code DRAFT} on creation; {@code ACTIVE} once published; {@code
 * HIDDEN} when withheld; {@code MERGED} when folded into another property (terminal). Mirrors the
 * {@code status in (...)} check constraint on {@code properties.property}.
 */
public enum PropertyStatus {
  DRAFT,
  ACTIVE,
  MERGED,
  HIDDEN
}
