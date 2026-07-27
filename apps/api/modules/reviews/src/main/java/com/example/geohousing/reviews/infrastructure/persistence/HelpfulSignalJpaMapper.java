package com.example.geohousing.reviews.infrastructure.persistence;

import com.example.geohousing.reviews.domain.HelpfulSignal;
import com.example.geohousing.reviews.domain.HelpfulSignalId;
import com.example.geohousing.reviews.domain.HelpfulSignalVoterId;
import com.example.geohousing.reviews.domain.ReviewId;

/**
 * Maps helpful signals without ever turning the opaque voter UUID into a cross-module association.
 */
final class HelpfulSignalJpaMapper {

  private HelpfulSignalJpaMapper() {}

  static HelpfulSignalJpaEntity toEntity(HelpfulSignal signal) {
    return new HelpfulSignalJpaEntity(
        signal.id().value(),
        signal.reviewId().value(),
        signal.voterId().value(),
        signal.createdAt(),
        signal.withdrawnAt());
  }

  static HelpfulSignal toDomain(HelpfulSignalJpaEntity entity) {
    return HelpfulSignal.reconstitute(
        HelpfulSignalId.of(entity.id()),
        ReviewId.of(entity.reviewId()),
        HelpfulSignalVoterId.of(entity.voterAccountId()),
        entity.createdAt(),
        entity.withdrawnAt());
  }
}
