package com.example.geohousing.verification.infrastructure.persistence;

import com.example.geohousing.verification.domain.EvidenceId;
import com.example.geohousing.verification.domain.VerificationCaseId;
import com.example.geohousing.verification.domain.VerificationEvidence;

final class VerificationEvidenceJpaMapper {

  private VerificationEvidenceJpaMapper() {}

  static VerificationEvidenceJpaEntity toEntity(VerificationEvidence evidence) {
    return new VerificationEvidenceJpaEntity(
        evidence.id().value(),
        evidence.caseId().value(),
        evidence.storageReference(),
        evidence.contentType(),
        evidence.sizeBytes(),
        evidence.sha256(),
        evidence.retentionDeadline(),
        evidence.uploadedAt(),
        evidence.deletedAt().orElse(null));
  }

  static VerificationEvidence toDomain(VerificationEvidenceJpaEntity entity) {
    return VerificationEvidence.reconstitute(
        EvidenceId.of(entity.id()),
        VerificationCaseId.of(entity.caseId()),
        entity.storageKey(),
        entity.contentType(),
        entity.sizeBytes(),
        entity.sha256(),
        entity.retentionDeadline(),
        entity.uploadedAt(),
        entity.deletedAt());
  }
}
