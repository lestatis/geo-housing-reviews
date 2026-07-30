package com.example.geohousing.moderation.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataModerationCaseRepository extends JpaRepository<ModerationCaseJpaEntity, UUID> {

  /** Mirrors the partial unique index: CLOSED is the only status that frees the target's slot. */
  Optional<ModerationCaseJpaEntity> findByTargetTypeAndTargetIdAndStatusNot(
      String targetType, UUID targetId, String closedStatus);

  List<ModerationCaseJpaEntity> findByStatusOrderByOpenedAtAsc(String status);
}
