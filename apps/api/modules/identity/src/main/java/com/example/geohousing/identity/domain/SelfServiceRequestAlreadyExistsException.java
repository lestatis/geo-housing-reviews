package com.example.geohousing.identity.domain;

/**
 * Raised when a self-service request for the same account and idempotency key was recorded
 * concurrently. The unique constraint settles the race; the caller re-reads the existing record and
 * treats it as a replay (same request type) or a key conflict (different type).
 */
public class SelfServiceRequestAlreadyExistsException extends RuntimeException {

  public SelfServiceRequestAlreadyExistsException(String message) {
    super(message);
  }
}
