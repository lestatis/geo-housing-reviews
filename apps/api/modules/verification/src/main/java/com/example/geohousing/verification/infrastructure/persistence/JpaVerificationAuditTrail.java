package com.example.geohousing.verification.infrastructure.persistence;

import com.example.geohousing.shared.audit.AuditEntry;
import com.example.geohousing.shared.audit.AuditTrail;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verification's half of the audit timeline: decisions on relationship claims. The actor is absent
 * for a scheduled expiry, which nobody decided.
 */
@Repository
class JpaVerificationAuditTrail implements AuditTrail {

  private static final String MODULE = "verification";
  private static final String SUBJECT_TYPE = "VERIFICATION_CASE";

  private final SpringDataVerificationDecisionAuditEventRepository events;

  JpaVerificationAuditTrail(SpringDataVerificationDecisionAuditEventRepository events) {
    this.events = events;
  }

  @Override
  @Transactional(readOnly = true)
  public List<AuditEntry> recorded(Instant from, Instant until, UUID actorAccountId, int limit) {
    return events.findForTimeline(from, until, actorAccountId, Limit.of(limit)).stream()
        .map(JpaVerificationAuditTrail::toEntry)
        .toList();
  }

  private static AuditEntry toEntry(VerificationDecisionAuditEventJpaEntity entity) {
    return new AuditEntry(
        entity.createdAt(),
        entity.actorAccountId(),
        MODULE,
        entity.action().name(),
        SUBJECT_TYPE,
        entity.caseId() == null ? null : entity.caseId().toString(),
        entity.outcome().name(),
        entity.reasonCode());
  }
}
