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
   * Entries in a window, newest first, optionally narrowed to one actor. The null check on the
   * parameter keeps "everyone" and "this person" as one query rather than two code paths.
   */
  @Query(
      "select e from AdminAuditEventJpaEntity e"
          + " where e.createdAt >= :from and e.createdAt < :until"
          + " and (:actorAccountId is null or e.adminAccountId = :actorAccountId)"
          + " order by e.createdAt desc, e.id desc")
  List<AdminAuditEventJpaEntity> findForTimeline(
      @Param("from") Instant from,
      @Param("until") Instant until,
      @Param("actorAccountId") UUID actorAccountId,
      Limit limit);
}
