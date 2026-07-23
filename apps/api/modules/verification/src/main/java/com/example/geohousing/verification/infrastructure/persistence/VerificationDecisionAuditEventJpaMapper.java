package com.example.geohousing.verification.infrastructure.persistence;

import com.example.geohousing.verification.domain.VerificationDecisionAuditEvent;

final class VerificationDecisionAuditEventJpaMapper {

  private VerificationDecisionAuditEventJpaMapper() {}

  static VerificationDecisionAuditEventJpaEntity toEntity(VerificationDecisionAuditEvent event) {
    return new VerificationDecisionAuditEventJpaEntity(
        event.id(),
        event.actorAccountId().orElse(null),
        event.action(),
        event.caseId().value(),
        event.reasonCode(),
        event.outcome(),
        event.occurredAt());
  }
}
