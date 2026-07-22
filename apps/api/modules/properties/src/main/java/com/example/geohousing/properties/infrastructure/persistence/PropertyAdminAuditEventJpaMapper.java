package com.example.geohousing.properties.infrastructure.persistence;

import com.example.geohousing.properties.domain.PropertyAdminAuditEvent;
import com.example.geohousing.properties.domain.PropertyId;

final class PropertyAdminAuditEventJpaMapper {

  private PropertyAdminAuditEventJpaMapper() {}

  static PropertyAdminAuditEventJpaEntity toEntity(PropertyAdminAuditEvent event) {
    return new PropertyAdminAuditEventJpaEntity(
        event.id(),
        event.adminId().value(),
        event.action(),
        event.propertyId().value(),
        event.targetPropertyId().map(PropertyId::value).orElse(null),
        event.outcome(),
        event.occurredAt());
  }
}
