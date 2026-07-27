package com.example.geohousing.verification.infrastructure.persistence;

import com.example.geohousing.verification.domain.EvidenceAccessEvent;

final class EvidenceAccessEventJpaMapper {

  private EvidenceAccessEventJpaMapper() {}

  static EvidenceAccessEventJpaEntity toEntity(EvidenceAccessEvent event) {
    return new EvidenceAccessEventJpaEntity(
        event.id(),
        event.evidenceId().value(),
        event.accessorAccountId().orElse(null),
        event.action(),
        event.occurredAt());
  }
}
