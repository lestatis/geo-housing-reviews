package com.example.geohousing.identity.infrastructure.persistence;

import com.example.geohousing.shared.audit.AuditCursor;
import com.example.geohousing.shared.audit.AuditEntry;
import com.example.geohousing.shared.audit.AuditTrail;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Identity's half of the audit timeline: who looked at, promoted, or restricted an account. */
@Repository
class JpaIdentityAuditTrail implements AuditTrail {

  private static final String MODULE = "identity";
  private static final String SUBJECT_TYPE = "ACCOUNT";

  private final SpringDataAdminAuditEventRepository events;

  JpaIdentityAuditTrail(SpringDataAdminAuditEventRepository events) {
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
        .map(JpaIdentityAuditTrail::toEntry)
        .toList();
  }

  private static AuditEntry toEntry(AdminAuditEventJpaEntity entity) {
    return new AuditEntry(
        entity.id(),
        entity.createdAt(),
        entity.adminAccountId(),
        MODULE,
        entity.action().name(),
        SUBJECT_TYPE,
        entity.targetAccountId() == null ? null : entity.targetAccountId().toString(),
        entity.outcome().name(),
        // Identity's actions carry no reason code: a role change is explained by the action itself,
        // and a restriction's reason belongs to the restriction rather than to the audit row.
        null);
  }
}
