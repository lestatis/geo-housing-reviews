package com.example.geohousing.identity.domain;

/**
 * Raised when an operation is attempted against a closed account — including re-provisioning an
 * account whose subject was previously deleted (closure is terminal; there is no resurrection).
 */
public class AccountClosedException extends RuntimeException {

  public AccountClosedException(String message) {
    super(message);
  }
}
