package com.example.geohousing.verification.domain;

/**
 * Raised when a verification case is asked to make a transition its current status does not allow.
 */
public class IllegalVerificationStateTransitionException extends RuntimeException {

  public IllegalVerificationStateTransitionException(String message) {
    super(message);
  }
}
