package com.example.geohousing.moderation.infrastructure.persistence;

import com.example.geohousing.shared.audit.AuditEntry;
import com.example.geohousing.shared.audit.AuditTrail;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moderation's half of the audit timeline.
 *
 * <p>This module keeps no separate audit table: a decision <em>is</em> the record, append-only by
 * design so an appeal can see what was decided rather than what a decision later became.
 *
 * <p>The internal note is deliberately not carried across. It is a moderator writing to another
 * moderator, it lives on the case where its context is, and a merged timeline is not a reason to
 * widen where it can be read. The public explanation is not carried either — the timeline says what
 * happened, and what the author was told belongs with the decision.
 */
@Repository
class JpaModerationAuditTrail implements AuditTrail {

  private static final String MODULE = "moderation";
  private static final String SUBJECT_TYPE = "MODERATION_CASE";

  private final SpringDataModerationDecisionRepository decisions;

  JpaModerationAuditTrail(SpringDataModerationDecisionRepository decisions) {
    this.decisions = decisions;
  }

  @Override
  @Transactional(readOnly = true)
  public List<AuditEntry> recorded(Instant from, Instant until, UUID actorAccountId, int limit) {
    return decisions.findForTimeline(from, until, actorAccountId, Limit.of(limit)).stream()
        .map(JpaModerationAuditTrail::toEntry)
        .toList();
  }

  private static AuditEntry toEntry(ModerationDecisionJpaEntity decision) {
    return new AuditEntry(
        decision.decidedAt(),
        decision.decidedByAccountId(),
        MODULE,
        decision.action(),
        SUBJECT_TYPE,
        decision.caseId() == null ? null : decision.caseId().toString(),
        // A recorded decision is one that was applied; there is no failed-decision row to describe.
        "APPLIED",
        decision.reasonCode());
  }
}
