package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.VerificationCaseId;

/** Raised when a Tier 2 case is approved before any retained document evidence is attached. */
public class DocumentEvidenceRequiredException extends RuntimeException {

  public DocumentEvidenceRequiredException(VerificationCaseId caseId) {
    super("document evidence is required before approving verification case: " + caseId.value());
  }
}
