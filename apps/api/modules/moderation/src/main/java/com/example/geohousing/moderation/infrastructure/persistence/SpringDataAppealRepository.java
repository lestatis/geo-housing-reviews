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

  /**
   * Appeals that reached an outcome in a window. Keyed on {@code decidedAt}, which is null until
   * one does — a pending appeal has not been heard, and counting it would flatter the queue.
   */
  @Query(
      "select count(a) from AppealJpaEntity a"
          + " where a.decidedAt >= :from and a.decidedAt < :until")
  long countDecidedBetween(@Param("from") Instant from, @Param("until") Instant until);

  /**
   * Of those, the ones with this outcome. {@code docs/MODERATION.md} asks for overturns by name: it
   * is the measurement that says whether decisions are made well, not merely quickly.
   */
  @Query(
      "select count(a) from AppealJpaEntity a"
          + " where a.status = :status and a.decidedAt >= :from and a.decidedAt < :until")
  long countWithStatusDecidedBetween(
      @Param("status") String status, @Param("from") Instant from, @Param("until") Instant until);
}
