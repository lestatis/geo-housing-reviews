package com.example.geohousing.verification.infrastructure.persistence;

import com.example.geohousing.verification.domain.VerificationDecisionAction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Append-only verification-decision audit rows; the application only ever inserts. */
interface SpringDataVerificationDecisionAuditEventRepository
    extends JpaRepository<VerificationDecisionAuditEventJpaEntity, UUID> {

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
      "select e from VerificationDecisionAuditEventJpaEntity e"
          + " where e.createdAt >= :from"
          + " and (e.createdAt < :beforeAt"
          + "      or (e.createdAt = :beforeAt and e.id < :beforeId))"
          + " and (:actorAccountId is null or e.actorAccountId = :actorAccountId)"
          + " order by e.createdAt desc, e.id desc")
  List<VerificationDecisionAuditEventJpaEntity> findForTimeline(
      @Param("from") Instant from,
      @Param("beforeAt") Instant beforeAt,
      @Param("beforeId") UUID beforeId,
      @Param("actorAccountId") UUID actorAccountId,
      Limit limit);

  /**
   * How often one decision was recorded in a window, {@code from} inclusive, {@code until}
   * exclusive.
   */
  @Query(
      "select count(e) from VerificationDecisionAuditEventJpaEntity e"
          + " where e.action = :action and e.createdAt >= :from and e.createdAt < :until")
  long countActionBetween(
      @Param("action") VerificationDecisionAction action,
      @Param("from") Instant from,
      @Param("until") Instant until);
}
