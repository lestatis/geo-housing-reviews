package com.example.geohousing.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataUserRestrictionRepository
    extends JpaRepository<UserRestrictionJpaEntity, UUID> {

  /**
   * Restrictions whose window could contain {@code asOf}: already started and not yet ended. Uses
   * the {@code user_restriction_account_active_idx} index. The boundaries mirror {@code
   * UserRestriction.isActiveAt}, which stays the authoritative gate on the domain side.
   */
  @Query(
      "select r from UserRestrictionJpaEntity r"
          + " where r.accountId = :accountId"
          + " and r.startAt <= :asOf"
          + " and (r.endAt is null or r.endAt > :asOf)")
  List<UserRestrictionJpaEntity> findActive(
      @Param("accountId") UUID accountId, @Param("asOf") Instant asOf);
}
