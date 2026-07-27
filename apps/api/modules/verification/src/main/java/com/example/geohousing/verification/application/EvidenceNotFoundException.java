package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.EvidenceId;

/**
 * Raised when evidence cannot be found for a given identifier — or when the caller is not allowed
 * to know it exists. Evidence belongs to a private workflow, so anything the caller may not see is
 * reported as missing rather than forbidden.
 */
public class EvidenceNotFoundException extends RuntimeException {

  public EvidenceNotFoundException(EvidenceId evidenceId) {
    super("verification evidence not found: " + evidenceId.value());
  }
}
