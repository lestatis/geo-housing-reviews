package com.example.geohousing.verification.infrastructure.persistence;

import com.example.geohousing.verification.domain.VerificationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataVerificationCaseRepository
    extends JpaRepository<VerificationCaseJpaEntity, UUID> {

  /**
   * The account's live case for a property. The excluded statuses match the partial unique index in
   * {@code V5.1}, so this sees exactly what the database would refuse to duplicate.
   */
  @Query(
      """
      select c from VerificationCaseJpaEntity c
      where c.accountId = :accountId and c.propertyId = :propertyId
        and c.status in (com.example.geohousing.verification.domain.VerificationStatus.PENDING,
                         com.example.geohousing.verification.domain.VerificationStatus.APPROVED)
      """)
  Optional<VerificationCaseJpaEntity> findLive(
      @Param("accountId") UUID accountId, @Param("propertyId") UUID propertyId);

  /** The account's most recent case for a property, whatever its status. */
  @Query(
      """
      select c from VerificationCaseJpaEntity c
      where c.accountId = :accountId and c.propertyId = :propertyId
      order by c.createdAt desc, c.id desc
      """)
  List<VerificationCaseJpaEntity> findLatest(
      @Param("accountId") UUID accountId, @Param("propertyId") UUID propertyId, Limit limit);

  /** Cases in a status, oldest first — the moderator queue reads {@code PENDING}. */
  @Query(
      """
      select c from VerificationCaseJpaEntity c
      where c.status = :status
      order by c.createdAt asc, c.id asc
      """)
  List<VerificationCaseJpaEntity> findByStatus(
      @Param("status") VerificationStatus status, Limit limit);
}
