package com.example.geohousing.reviews.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Append-only moderation audit rows; the application only ever inserts. */
interface SpringDataReviewModerationAuditEventRepository
    extends JpaRepository<ReviewModerationAuditEventJpaEntity, UUID> {}
