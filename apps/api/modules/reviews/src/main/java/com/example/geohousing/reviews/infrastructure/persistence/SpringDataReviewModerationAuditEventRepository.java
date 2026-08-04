package com.example.geohousing.reviews.infrastructure.persistence;

import com.example.geohousing.reviews.domain.ReviewModerationAction;
import com.example.geohousing.reviews.domain.ReviewModerationOutcome;
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
   * A page of the timeline, newest first, resumed from a cursor.
   *
   * <p>Keyset rather than offset: the caller merges five sources, and an offset into one of them
   * means nothing in the merged order. {@code beforeAt} with {@code beforeId} expresses "strictly
   * after this position", where the id bound is whatever the cursor says this module's rows must
   * sort below at that exact instant.
   *
   * <p>The null check on the actor keeps "everyone" and "this person" as one query rather than two
   * code paths.
   */
  @Query(
      "select e from ReviewModerationAuditEventJpaEntity e"
          + " where e.createdAt >= :from"
          + " and (e.createdAt < :beforeAt"
          + "      or (e.createdAt = :beforeAt and e.id < :beforeId))"
          + " and (:actorAccountId is null or e.moderatorAccountId = :actorAccountId)"
          + " order by e.createdAt desc, e.id desc")
  List<ReviewModerationAuditEventJpaEntity> findForTimeline(
      @Param("from") Instant from,
      @Param("beforeAt") Instant beforeAt,
      @Param("beforeId") UUID beforeId,
      @Param("actorAccountId") UUID actorAccountId,
      Limit limit);

  /**
   * How often one moderation action was actually applied in a window, {@code from} inclusive and
   * {@code until} exclusive — the same half-open rule as the timeline, so the two cannot disagree
   * about which day an action fell on.
   *
   * <p>{@code APPLIED} only. This table also records attempts that found nothing: a moderator
   * publishing a review that no longer exists is an auditable event, and rightly so, but no review
   * was published. Counting it would tell an administrator that work was completed which was not.
   */
  @Query(
      "select count(e) from ReviewModerationAuditEventJpaEntity e"
          + " where e.action = :action and e.outcome = :outcome"
          + " and e.createdAt >= :from and e.createdAt < :until")
  long countActionBetween(
      @Param("action") ReviewModerationAction action,
      @Param("outcome") ReviewModerationOutcome outcome,
      @Param("from") Instant from,
      @Param("until") Instant until);
}
