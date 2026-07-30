package com.example.geohousing.moderation.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAppealRepository extends JpaRepository<AppealJpaEntity, UUID> {

  Optional<AppealJpaEntity> findByDecisionId(UUID decisionId);

  List<AppealJpaEntity> findByStatusOrderByCreatedAtAsc(String status);
}
