package com.example.geohousing.identity.infrastructure.persistence;

import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AdminAuditEvent;

final class AdminAuditEventJpaMapper {

  private AdminAuditEventJpaMapper() {}

  static AdminAuditEventJpaEntity toEntity(AdminAuditEvent event) {
    return new AdminAuditEventJpaEntity(
        event.id(),
        event.adminAccountId().value(),
        event.action(),
        event.targetAccountId().map(AccountId::value).orElse(null),
        event.outcome(),
        event.occurredAt());
  }

  static AdminAuditEvent toDomain(AdminAuditEventJpaEntity entity) {
    return AdminAuditEvent.reconstitute(
        entity.id(),
        AccountId.of(entity.adminAccountId()),
        entity.action(),
        entity.targetAccountId() == null ? null : AccountId.of(entity.targetAccountId()),
        entity.outcome(),
        entity.createdAt());
  }
}
