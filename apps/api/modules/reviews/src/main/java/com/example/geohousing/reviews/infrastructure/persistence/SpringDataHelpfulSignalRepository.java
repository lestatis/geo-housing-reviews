package com.example.geohousing.reviews.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataHelpfulSignalRepository extends JpaRepository<HelpfulSignalJpaEntity, UUID> {

  Optional<HelpfulSignalJpaEntity> findByReviewIdAndVoterAccountIdAndWithdrawnAtIsNull(
      UUID reviewId, UUID voterAccountId);

  long countByReviewIdAndWithdrawnAtIsNull(UUID reviewId);

  /**
   * Grouped active totals for a set of reviews. Returns only review ids and counts — no voter
   * column is selected, so a listing projection cannot accidentally carry voter identities.
   */
  @Query(
      """
      select s.reviewId as reviewId, count(s) as total
      from HelpfulSignalJpaEntity s
      where s.reviewId in :reviewIds and s.withdrawnAt is null
      group by s.reviewId
      """)
  List<HelpfulSignalCountProjection> countActiveGrouped(
      @Param("reviewIds") Collection<UUID> reviewIds);

  /** Row shape of {@link #countActiveGrouped}: a review id and its active total, nothing else. */
  interface HelpfulSignalCountProjection {
    UUID getReviewId();

    long getTotal();
  }
}
