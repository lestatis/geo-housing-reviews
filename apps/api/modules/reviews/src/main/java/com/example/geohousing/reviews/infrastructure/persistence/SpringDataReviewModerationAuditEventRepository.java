package com.example.geohousing.reviews.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Append-only moderation audit rows; the application only ever inserts. */
interface SpringDataReviewModerationAuditEventRepository
    extends JpaRepository<ReviewModerationAuditEventJpaEntity, UUID> {

  /**
   * Entries in a window, newest first, optionally narrowed to one actor. The null check keeps
   * "everyone" and "this person" as one query rather than two code paths.
   */
  @Query(
      "select e from ReviewModerationAuditEventJpaEntity e"
          + " where e.createdAt >= :from and e.createdAt < :until"
          + " and (:actorAccountId is null or e.moderatorAccountId = :actorAccountId)"
          + " order by e.createdAt desc, e.id desc")
  List<ReviewModerationAuditEventJpaEntity> findForTimeline(
      @Param("from") Instant from,
      @Param("until") Instant until,
      @Param("actorAccountId") UUID actorAccountId,
      Limit limit);
}
