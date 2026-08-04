package com.example.geohousing.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataAdminAuditEventRepository
    extends JpaRepository<AdminAuditEventJpaEntity, UUID> {

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
      "select e from AdminAuditEventJpaEntity e"
          + " where e.createdAt >= :from"
          + " and (e.createdAt < :beforeAt"
          + "      or (e.createdAt = :beforeAt and e.id < :beforeId))"
          + " and (:actorAccountId is null or e.adminAccountId = :actorAccountId)"
          + " order by e.createdAt desc, e.id desc")
  List<AdminAuditEventJpaEntity> findForTimeline(
      @Param("from") Instant from,
      @Param("beforeAt") Instant beforeAt,
      @Param("beforeId") UUID beforeId,
      @Param("actorAccountId") UUID actorAccountId,
      Limit limit);
}
