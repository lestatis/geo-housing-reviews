package com.example.geohousing.verification.infrastructure.web;

import com.example.geohousing.verification.domain.VerificationEvidence;
import java.time.Instant;

/**
 * Safe metadata returned after upload and in a moderator's evidence list; never includes bytes or a
 * storage key.
 */
public record VerificationEvidenceResponse(
    String evidenceId, String contentType, long sizeBytes, Instant uploadedAt, Instant deletedAt) {

  static VerificationEvidenceResponse from(VerificationEvidence evidence) {
    return new VerificationEvidenceResponse(
        evidence.id().value().toString(),
        evidence.contentType(),
        evidence.sizeBytes(),
        evidence.uploadedAt(),
        evidence.deletedAt().orElse(null));
  }
}
