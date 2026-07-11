package com.example.geohousing.identity.domain;

/** Raised when a pseudonym is already taken by another public profile. */
public class PseudonymAlreadyInUseException extends RuntimeException {

  public PseudonymAlreadyInUseException(Pseudonym pseudonym) {
    super("pseudonym already in use: " + pseudonym.value());
  }
}
