package com.example.geohousing.reviews.infrastructure.persistence;

import com.example.geohousing.shared.audit.AuditEntry;
import com.example.geohousing.shared.audit.AuditTrail;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reviews' half of the audit timeline: what a moderator did to a review, and the reason code they
 * gave.
 */
@Repository
class JpaReviewsAuditTrail implements AuditTrail {

  private static final String MODULE = "reviews";
  private static final String SUBJECT_TYPE = "REVIEW";

  private final SpringDataReviewModerationAuditEventRepository events;

  JpaReviewsAuditTrail(SpringDataReviewModerationAuditEventRepository events) {
    this.events = events;
  }

  @Override
  @Transactional(readOnly = true)
  public List<AuditEntry> recorded(Instant from, Instant until, UUID actorAccountId, int limit) {
    return events.findForTimeline(from, until, actorAccountId, Limit.of(limit)).stream()
        .map(JpaReviewsAuditTrail::toEntry)
        .toList();
  }

  private static AuditEntry toEntry(ReviewModerationAuditEventJpaEntity entity) {
    return new AuditEntry(
        entity.createdAt(),
        entity.moderatorAccountId(),
        MODULE,
        entity.action().name(),
        SUBJECT_TYPE,
        entity.reviewId() == null ? null : entity.reviewId().toString(),
        entity.outcome().name(),
        entity.reasonCode());
  }
}
