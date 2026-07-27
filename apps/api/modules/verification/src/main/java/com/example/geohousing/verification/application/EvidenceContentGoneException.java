package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.EvidenceId;

/**
 * Raised when evidence metadata exists but its object is no longer in storage — the normal state
 * after retention deletion, and the honest answer to a moderator asking to read it. Deliberately
 * distinct from "not found": the record of the upload survives deletion by design.
 */
public class EvidenceContentGoneException extends RuntimeException {

  public EvidenceContentGoneException(EvidenceId evidenceId) {
    super("the evidence object has been deleted under the retention policy: " + evidenceId.value());
  }
}
