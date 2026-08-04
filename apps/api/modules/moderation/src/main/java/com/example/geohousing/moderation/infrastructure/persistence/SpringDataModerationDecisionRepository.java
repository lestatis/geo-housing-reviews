package com.example.geohousing.moderation.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataModerationDecisionRepository
    extends JpaRepository<ModerationDecisionJpaEntity, UUID> {

  /**
   * Ordered by time, then id. The tiebreaker matters: two decisions on one case can share an
   * instant, and an audit trail that reorders itself between reads is not one an appeal can rely
   * on.
   */
  List<ModerationDecisionJpaEntity> findByCaseIdOrderByDecidedAtAscIdAsc(UUID caseId);

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
      "select d from ModerationDecisionJpaEntity d"
          + " where d.decidedAt >= :from"
          + " and (d.decidedAt < :beforeAt"
          + "      or (d.decidedAt = :beforeAt and d.id < :beforeId))"
          + " and (:actorAccountId is null or d.decidedByAccountId = :actorAccountId)"
          + " order by d.decidedAt desc, d.id desc")
  List<ModerationDecisionJpaEntity> findForTimeline(
      @Param("from") Instant from,
      @Param("beforeAt") Instant beforeAt,
      @Param("beforeId") UUID beforeId,
      @Param("actorAccountId") UUID actorAccountId,
      Limit limit);

  /**
   * Decisions recorded in a window, {@code from} inclusive and {@code until} exclusive — the same
   * half-open rule the timeline uses, so the two never disagree about which day a decision fell on.
   */
  @Query(
      "select count(d) from ModerationDecisionJpaEntity d"
          + " where d.decidedAt >= :from and d.decidedAt < :until")
  long countDecidedBetween(@Param("from") Instant from, @Param("until") Instant until);
}
