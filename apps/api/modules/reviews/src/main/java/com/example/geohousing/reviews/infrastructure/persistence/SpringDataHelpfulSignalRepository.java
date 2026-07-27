package com.example.geohousing.reviews.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataHelpfulSignalRepository extends JpaRepository<HelpfulSignalJpaEntity, UUID> {

  Optional<HelpfulSignalJpaEntity> findByReviewIdAndVoterAccountIdAndWithdrawnAtIsNull(
      UUID reviewId, UUID voterAccountId);

  long countByReviewIdAndWithdrawnAtIsNull(UUID reviewId);
}
