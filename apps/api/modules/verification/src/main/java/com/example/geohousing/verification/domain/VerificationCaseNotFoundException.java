package com.example.geohousing.verification.domain;

/**
 * Raised when a verification case cannot be found for a given identifier — or when the viewer is
 * not allowed to know it exists. A verification case is a private workflow, so a case the caller
 * may not see is reported as missing rather than forbidden.
 */
public class VerificationCaseNotFoundException extends RuntimeException {

  public VerificationCaseNotFoundException(VerificationCaseId caseId) {
    super("verification case not found: " + caseId.value());
  }
}
