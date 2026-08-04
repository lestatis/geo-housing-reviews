package com.example.geohousing.properties.infrastructure.persistence;

import com.example.geohousing.shared.audit.AuditCursor;
import com.example.geohousing.shared.audit.AuditEntry;
import com.example.geohousing.shared.audit.AuditTrail;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Properties' half of the audit timeline: which catalogue records an administrator activated,
 * withdrew or merged.
 */
@Repository
class JpaPropertiesAuditTrail implements AuditTrail {

  private static final String MODULE = "properties";
  private static final String SUBJECT_TYPE = "PROPERTY";

  private final SpringDataPropertyAdminAuditEventRepository events;

  JpaPropertiesAuditTrail(SpringDataPropertyAdminAuditEventRepository events) {
    this.events = events;
  }

  @Override
  @Transactional(readOnly = true)
  public List<AuditEntry> recorded(
      Instant from, AuditCursor before, UUID actorAccountId, int limit) {
    return events
        .findForTimeline(
            from, before.at(), before.idBoundFor(MODULE), actorAccountId, Limit.of(limit))
        .stream()
        .map(JpaPropertiesAuditTrail::toEntry)
        .toList();
  }

  private static AuditEntry toEntry(PropertyAdminAuditEventJpaEntity entity) {
    return new AuditEntry(
        entity.id(),
        entity.createdAt(),
        entity.adminAccountId(),
        MODULE,
        entity.action().name(),
        SUBJECT_TYPE,
        entity.propertyId() == null ? null : entity.propertyId().toString(),
        entity.outcome().name(),
        null);
  }
}
