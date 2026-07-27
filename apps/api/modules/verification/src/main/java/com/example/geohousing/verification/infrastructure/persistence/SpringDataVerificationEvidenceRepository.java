package com.example.geohousing.verification.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataVerificationEvidenceRepository
    extends JpaRepository<VerificationEvidenceJpaEntity, UUID> {

  @Query(
      """
      select e from VerificationEvidenceJpaEntity e
      where e.caseId = :caseId
      order by e.uploadedAt asc, e.id asc
      """)
  List<VerificationEvidenceJpaEntity> findByCase(@Param("caseId") UUID caseId);

  /**
   * Evidence past its deadline whose object has not been deleted — the sweep's input. The {@code
   * deletedAt is null} predicate matches the partial index from {@code V5.2}.
   */
  @Query(
      """
      select e from VerificationEvidenceJpaEntity e
      where e.deletedAt is null and e.retentionDeadline <= :asOf
      order by e.retentionDeadline asc, e.id asc
      """)
  List<VerificationEvidenceJpaEntity> findPastRetention(@Param("asOf") Instant asOf, Limit limit);
}
