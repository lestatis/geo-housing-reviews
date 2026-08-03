package com.example.geohousing.identity.api;

/**
 * The acting account is restricted and may not contribute.
 *
 * <p>Published so a calling module can refuse without inventing its own vocabulary for the same
 * fact. Deliberately carries no reason: what the affected person is told about their restriction is
 * identity's to say, in one place, rather than something each module phrases for itself.
 */
public class RestrictedAccountException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public RestrictedAccountException(String message) {
    super(message);
  }
}
