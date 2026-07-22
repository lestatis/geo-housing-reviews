package com.example.geohousing.reviews.domain;

/**
 * Raised when a review is written from a stale version — the version the caller loaded no longer
 * matches the stored one (API_GUIDELINES: conflict when acting on stale content).
 */
public class ReviewVersionConflictException extends RuntimeException {

  public ReviewVersionConflictException(String message) {
    super(message);
  }
}
