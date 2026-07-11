package com.example.geohousing.identity.domain;

/** Raised when an account cannot be found for a given identifier. */
public class AccountNotFoundException extends RuntimeException {

  public AccountNotFoundException(AccountId accountId) {
    super("account not found: " + accountId.value());
  }

  public AccountNotFoundException(String message) {
    super(message);
  }
}
