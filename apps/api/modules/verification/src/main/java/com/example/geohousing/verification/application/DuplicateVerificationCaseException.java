package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.PropertyRef;
import com.example.geohousing.verification.domain.VerificationCaseId;

/**
 * Raised when an account already has a live case for the property. One account holds one live case
 * (pending or approved) per property; the existing case's id is carried so the caller can point the
 * account at it instead of opening a second.
 */
public class DuplicateVerificationCaseException extends RuntimeException {

  private final transient VerificationCaseId existingCaseId;

  public DuplicateVerificationCaseException(PropertyRef propertyRef, VerificationCaseId existing) {
    super("account already has a live verification case for property " + propertyRef.value());
    this.existingCaseId = existing;
  }

  public VerificationCaseId existingCaseId() {
    return existingCaseId;
  }
}
