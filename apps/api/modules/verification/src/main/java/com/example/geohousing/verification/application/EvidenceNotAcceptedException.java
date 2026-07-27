package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.VerificationCaseId;

/**
 * Raised when a case will not take evidence: it is not a document-method case, or it has already
 * been decided. Distinct from "no such case", which is reported as not found.
 */
public class EvidenceNotAcceptedException extends RuntimeException {

  public EvidenceNotAcceptedException(VerificationCaseId caseId) {
    super("verification case is not accepting evidence: " + caseId.value());
  }
}
