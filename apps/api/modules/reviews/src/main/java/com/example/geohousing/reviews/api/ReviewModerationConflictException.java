package com.example.geohousing.reviews.api;

/**
 * The review changed since the moderator loaded it, so the requested change was refused.
 *
 * <p>A published-contract type: the internal {@code ReviewVersionConflictException} is a domain
 * class and must not cross the boundary, or callers end up catching another module's internals.
 */
public class ReviewModerationConflictException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ReviewModerationConflictException(String message) {
    super(message);
  }
}
