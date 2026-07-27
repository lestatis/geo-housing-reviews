package com.example.geohousing.verification.infrastructure.web;

import com.example.geohousing.verification.domain.VerificationEvidence;
import java.util.List;

/**
 * Evidence metadata for one moderation case. Document bytes are retrieved only through the audited
 * read endpoint.
 */
public record VerificationEvidenceListResponse(List<VerificationEvidenceResponse> items) {

  static VerificationEvidenceListResponse from(List<VerificationEvidence> evidence) {
    return new VerificationEvidenceListResponse(
        evidence.stream().map(VerificationEvidenceResponse::from).toList());
  }
}
