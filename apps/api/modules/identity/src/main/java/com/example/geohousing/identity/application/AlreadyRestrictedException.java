package com.example.geohousing.identity.application;

/**
 * An active restriction already covers this account in this scope.
 *
 * <p>Stacking them makes the end date meaningless — whichever ends last silently wins, and an
 * account told "restricted until the 8th" stays restricted past it. Lift the existing one, or place
 * a longer one deliberately.
 */
public class AlreadyRestrictedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public AlreadyRestrictedException(String message) {
    super(message);
  }
}
