package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.HelpfulSignal;
import com.example.geohousing.reviews.domain.HelpfulSignalId;
import com.example.geohousing.reviews.domain.HelpfulSignalVoterId;
import com.example.geohousing.reviews.domain.ReviewId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory helpful-signal port for application tests. */
final class InMemoryHelpfulSignalRepository implements HelpfulSignalRepository {

  final Map<HelpfulSignalId, HelpfulSignal> byId = new LinkedHashMap<>();

  @Override
  public Optional<HelpfulSignal> findActive(ReviewId reviewId, HelpfulSignalVoterId voterId) {
    return byId.values().stream()
        .filter(signal -> signal.reviewId().equals(reviewId))
        .filter(signal -> signal.voterId().equals(voterId))
        .filter(HelpfulSignal::isActive)
        .findFirst()
        .map(InMemoryHelpfulSignalRepository::snapshot);
  }

  @Override
  public void create(HelpfulSignal signal) {
    byId.put(signal.id(), snapshot(signal));
  }

  @Override
  public void withdraw(HelpfulSignal signal) {
    byId.put(signal.id(), snapshot(signal));
  }

  @Override
  public long countActive(ReviewId reviewId) {
    return byId.values().stream()
        .filter(signal -> signal.reviewId().equals(reviewId))
        .filter(HelpfulSignal::isActive)
        .count();
  }

  @Override
  public Map<ReviewId, Long> countActive(Collection<ReviewId> reviewIds) {
    Map<ReviewId, Long> counts = new LinkedHashMap<>();
    for (ReviewId reviewId : reviewIds) {
      long active = countActive(reviewId);
      // Mirrors the real adapter, which returns no row for a review with no active signals.
      if (active > 0) {
        counts.put(reviewId, active);
      }
    }
    return counts;
  }

  private static HelpfulSignal snapshot(HelpfulSignal signal) {
    return HelpfulSignal.reconstitute(
        signal.id(), signal.reviewId(), signal.voterId(), signal.createdAt(), signal.withdrawnAt());
  }
}
