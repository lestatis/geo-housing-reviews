package com.example.geohousing.moderation.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataReportRepository extends JpaRepository<ReportJpaEntity, UUID> {

  /** Mirrors the partial unique index over live statuses. */
  Optional<ReportJpaEntity> findByReporterAccountIdAndTargetTypeAndTargetIdAndStatusIn(
      UUID reporterAccountId, String targetType, UUID targetId, List<String> liveStatuses);

  List<ReportJpaEntity> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
}
