package com.example.geohousing.reviews.infrastructure.persistence;

import com.example.geohousing.reviews.domain.ReviewModerationAuditEvent;

final class ReviewModerationAuditEventJpaMapper {

  private ReviewModerationAuditEventJpaMapper() {}

  static ReviewModerationAuditEventJpaEntity toEntity(ReviewModerationAuditEvent event) {
    return new ReviewModerationAuditEventJpaEntity(
        event.id(),
        event.moderatorId().value(),
        event.action(),
        event.reviewId().value(),
        event.reviewVersionId().orElse(null),
        event.reasonCode(),
        event.outcome(),
        event.occurredAt());
  }
}
