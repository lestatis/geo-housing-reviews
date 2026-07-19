package com.example.geohousing.identity.application;

/** Raised when an idempotency key is reused for a different kind of self-service request. */
public class IdempotencyKeyConflictException extends RuntimeException {

  public IdempotencyKeyConflictException(String message) {
    super(message);
  }
}
