package com.example.geohousing.identity.application;

/** No restriction exists for this identifier. */
public class RestrictionNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public RestrictionNotFoundException(String message) {
    super(message);
  }
}
