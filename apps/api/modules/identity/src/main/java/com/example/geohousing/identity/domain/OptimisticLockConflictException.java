package com.example.geohousing.identity.domain;

/**
 * Raised when a mutation is attempted against a stale version of an aggregate (the caller's
 * expected version no longer matches the current one).
 */
public class OptimisticLockConflictException extends RuntimeException {

  public OptimisticLockConflictException(String message) {
    super(message);
  }
}
