package com.example.geohousing.identity.application;

/**
 * Demoting this account would leave the platform with no administrator.
 *
 * <p>One request would otherwise leave nobody able to moderate, verify, or grant the role back —
 * recoverable only by editing the database, which is the thing the role endpoint exists to avoid.
 */
public class LastAdministratorException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public LastAdministratorException(String message) {
    super(message);
  }
}
