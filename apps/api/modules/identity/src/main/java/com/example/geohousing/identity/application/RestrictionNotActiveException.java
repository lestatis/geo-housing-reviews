package com.example.geohousing.identity.application;

/**
 * This restriction has already ended.
 *
 * <p>Lifting it again would move the end forward and rewrite when the account regained access.
 */
public class RestrictionNotActiveException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public RestrictionNotActiveException(String message) {
    super(message);
  }
}
