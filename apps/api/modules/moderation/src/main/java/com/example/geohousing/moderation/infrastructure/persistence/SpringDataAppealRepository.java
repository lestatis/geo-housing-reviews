package com.example.geohousing.moderation.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataAppealRepository extends JpaRepository<AppealJpaEntity, UUID> {

  Optional<AppealJpaEntity> findByDecisionId(UUID decisionId);

  List<AppealJpaEntity> findByStatusOrderByCreatedAtAsc(String status);

  /** One row per outcome, so both appeal numbers come from a single snapshot. */
  interface AppealOutcomeCount {
    String getStatus();

    long getTotal();
  }

  /**
   * Appeals that reached an outcome in a window, grouped by that outcome.
   *
   * <p>Keyed on {@code decidedAt}, which is null until one does — a pending appeal has not been
   * heard, and counting it would flatter the queue.
   *
   * <p><strong>One query, deliberately.</strong> Counting "heard" and "overturned" separately meant
   * two statements, and PostgreSQL's default READ COMMITTED gives each statement its own snapshot:
   * a moderator deciding an appeal between them could produce one overturned appeal out of zero
   * heard. Grouping makes the relationship structural — every overturned appeal is one of the rows
   * being summed — instead of an invariant checked after the fact and violated by ordinary use.
   */
  @Query(
      "select a.status as status, count(a) as total from AppealJpaEntity a"
          + " where a.decidedAt >= :from and a.decidedAt < :until"
          + " group by a.status")
  List<AppealOutcomeCount> countByOutcomeDecidedBetween(
      @Param("from") Instant from, @Param("until") Instant until);
}
