package com.example.geohousing.reviews.infrastructure.persistence;

import com.example.geohousing.reviews.api.ReviewMetrics;
import com.example.geohousing.reviews.api.ReviewThroughput;
import com.example.geohousing.reviews.domain.ReviewModerationAction;
import com.example.geohousing.reviews.domain.ReviewModerationOutcome;
import com.example.geohousing.reviews.domain.ReviewStatus;
import java.time.Instant;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Reviews' counts: what is waiting, and what moderation did to published content. */
@Repository
class JpaReviewMetrics implements ReviewMetrics {

  private final SpringDataReviewRepository reviews;
  private final SpringDataReviewModerationAuditEventRepository moderationEvents;

  JpaReviewMetrics(
      SpringDataReviewRepository reviews,
      SpringDataReviewModerationAuditEventRepository moderationEvents) {
    this.reviews = reviews;
    this.moderationEvents = moderationEvents;
  }

  @Override
  @Transactional(readOnly = true)
  public long awaitingModeration() {
    return reviews.countByStatus(ReviewStatus.PENDING_MODERATION);
  }

  @Override
  @Transactional(readOnly = true)
  public ReviewThroughput between(Instant from, Instant until) {
    // From the audit rows, not from current status: a review published on Monday and removed on
    // Friday is one publication that really happened, and today's status would erase it. Applied
    // outcomes only — an attempt that found nothing changed nothing.
    return new ReviewThroughput(
        moderationEvents.countActionBetween(
            ReviewModerationAction.PUBLISH, ReviewModerationOutcome.APPLIED, from, until),
        moderationEvents.countActionBetween(
            ReviewModerationAction.REMOVE, ReviewModerationOutcome.APPLIED, from, until));
  }
}
