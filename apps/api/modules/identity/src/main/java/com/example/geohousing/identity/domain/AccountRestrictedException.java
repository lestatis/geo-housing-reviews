package com.example.geohousing.identity.domain;

/**
 * Raised when an account with an active {@link UserRestriction} attempts a mutation it is not
 * permitted to perform while restricted.
 */
public class AccountRestrictedException extends RuntimeException {

  public AccountRestrictedException(String message) {
    super(message);
  }
}
