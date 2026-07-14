package com.example.geohousing.identity.domain;

/**
 * Raised when an account for the same auth-subject hash was created concurrently — two first
 * requests from the same new subject can both find no account and both try to insert. The unique
 * constraint on {@code auth_subject_hash} is the authority that settles the race; this exception is
 * how the loser reports it, so the caller can re-read the winner's account instead of surfacing an
 * infrastructure error.
 */
public class AuthSubjectAlreadyProvisionedException extends RuntimeException {

  public AuthSubjectAlreadyProvisionedException(String message) {
    super(message);
  }
}
