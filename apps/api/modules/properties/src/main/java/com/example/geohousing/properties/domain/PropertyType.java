package com.example.geohousing.properties.domain;

/**
 * The kind of reviewable object. Mirrors the {@code type in (...)} check constraint on {@code
 * properties.property}.
 */
public enum PropertyType {
  BUILDING,
  RESIDENTIAL_COMPLEX,
  BLOCK,
  PHASE
}
