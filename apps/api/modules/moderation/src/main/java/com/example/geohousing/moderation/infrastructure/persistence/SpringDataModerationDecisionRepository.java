package com.example.geohousing.moderation.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataModerationDecisionRepository
    extends JpaRepository<ModerationDecisionJpaEntity, UUID> {

  /**
   * Ordered by time, then id. The tiebreaker matters: two decisions on one case can share an
   * instant, and an audit trail that reorders itself between reads is not one an appeal can rely
   * on.
   */
  List<ModerationDecisionJpaEntity> findByCaseIdOrderByDecidedAtAscIdAsc(UUID caseId);
}
