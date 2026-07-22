package com.example.geohousing.reviews.infrastructure.persistence;

import com.example.geohousing.reviews.domain.ReviewStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataReviewRepository extends JpaRepository<ReviewJpaEntity, UUID> {

  /**
   * The author's review of a property that still occupies the one-live-review slot. The excluded
   * statuses match the partial unique index in {@code V4.1}, so this sees exactly what the database
   * would refuse to duplicate.
   */
  @Query(
      """
      select r from ReviewJpaEntity r
      where r.authorAccountId = :authorAccountId
        and r.propertyId = :propertyId
        and r.status not in (com.example.geohousing.reviews.domain.ReviewStatus.REJECTED,
                             com.example.geohousing.reviews.domain.ReviewStatus.REMOVED)
      """)
  Optional<ReviewJpaEntity> findLive(
      @Param("authorAccountId") UUID authorAccountId, @Param("propertyId") UUID propertyId);

  /**
   * First page of a property's published reviews, newest publication first with the id as
   * tie-breaker. Only ids: the limit has to be applied by the database, and a query that
   * fetch-joins a collection cannot be limited in SQL (Hibernate would page in memory).
   */
  @Query(
      """
      select r.id from ReviewJpaEntity r
      where r.propertyId = :propertyId and r.status = :status
      order by r.publishedAt desc, r.id desc
      """)
  List<UUID> findPublishedIds(
      @Param("propertyId") UUID propertyId, @Param("status") ReviewStatus status, Limit limit);

  /** The same listing, continued strictly after the cursor position. */
  @Query(
      """
      select r.id from ReviewJpaEntity r
      where r.propertyId = :propertyId and r.status = :status
        and (r.publishedAt < :cursorPublishedAt
             or (r.publishedAt = :cursorPublishedAt and r.id < :cursorId))
      order by r.publishedAt desc, r.id desc
      """)
  List<UUID> findPublishedIdsAfter(
      @Param("propertyId") UUID propertyId,
      @Param("status") ReviewStatus status,
      @Param("cursorPublishedAt") Instant cursorPublishedAt,
      @Param("cursorId") UUID cursorId,
      Limit limit);
}
