package com.example.geohousing.verification.domain;

/**
 * Raised when a verification case is written from a stale version — the version the caller loaded
 * no longer matches the stored one (API_GUIDELINES: conflict when acting on stale content).
 */
public class VerificationVersionConflictException extends RuntimeException {

  public VerificationVersionConflictException(String message) {
    super(message);
  }
}
