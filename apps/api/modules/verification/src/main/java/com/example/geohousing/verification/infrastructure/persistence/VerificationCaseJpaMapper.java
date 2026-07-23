package com.example.geohousing.verification.infrastructure.persistence;

import com.example.geohousing.verification.domain.AccountRef;
import com.example.geohousing.verification.domain.ModeratorId;
import com.example.geohousing.verification.domain.PropertyRef;
import com.example.geohousing.verification.domain.VerificationCase;
import com.example.geohousing.verification.domain.VerificationCaseId;

final class VerificationCaseJpaMapper {

  private VerificationCaseJpaMapper() {}

  static VerificationCaseJpaEntity toEntity(VerificationCase source) {
    return new VerificationCaseJpaEntity(
        source.id().value(),
        source.accountRef().value(),
        source.propertyRef().value(),
        source.relationshipClaim(),
        source.method(),
        source.status(),
        source.tier(),
        source.decisionReasonCode().orElse(null),
        source.policyVersion(),
        source.verifiedAt().orElse(null),
        source.validThrough().orElse(null),
        source.decidedBy().map(ModeratorId::value).orElse(null),
        source.createdAt(),
        source.updatedAt(),
        source.version());
  }

  static VerificationCase toDomain(VerificationCaseJpaEntity entity) {
    return VerificationCase.reconstitute(
        VerificationCaseId.of(entity.id()),
        AccountRef.of(entity.accountId()),
        PropertyRef.of(entity.propertyId()),
        entity.relationshipClaim(),
        entity.method(),
        entity.status(),
        entity.tier(),
        entity.decisionReasonCode(),
        entity.policyVersion(),
        entity.verifiedAt(),
        entity.validThrough(),
        entity.decidedBy() == null ? null : ModeratorId.of(entity.decidedBy()),
        entity.createdAt(),
        entity.updatedAt(),
        entity.version());
  }
}
