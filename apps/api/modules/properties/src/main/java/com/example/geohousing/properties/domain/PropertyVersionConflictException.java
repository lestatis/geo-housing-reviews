package com.example.geohousing.properties.domain;

/**
 * Raised when an admin acts on a stale version of a property — the version they saw no longer
 * matches the stored one (API_GUIDELINES: conflict when a moderator acts on stale content).
 */
public class PropertyVersionConflictException extends RuntimeException {

  public PropertyVersionConflictException(String message) {
    super(message);
  }
}
