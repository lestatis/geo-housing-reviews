package com.example.geohousing.identity.domain;

/** Raised when a pseudonym value violates the length or charset rules. */
public class InvalidPseudonymException extends RuntimeException {

  public InvalidPseudonymException(String message) {
    super(message);
  }
}
