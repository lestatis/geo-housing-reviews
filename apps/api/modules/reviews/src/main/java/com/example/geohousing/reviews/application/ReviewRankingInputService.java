package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.HelpfulnessInput;
import com.example.geohousing.reviews.domain.HelpfulnessInputPolicy;
import com.example.geohousing.reviews.domain.RankingInputVersion;
import com.example.geohousing.reviews.domain.ReviewId;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Supplies the bounded helpfulness ranking input for reviews (ARCHITECTURE.md §5 assigns ranking
 * inputs to this module).
 *
 * <p>Producing the input is deliberately separate from applying it. Nothing orders reviews by it
 * yet: the public listing stays newest-publication-first until ranking policy is approved, so this
 * service adds a signal a future ranking layer can consume without changing what any reader sees
 * today.
 */
public final class ReviewRankingInputService {

  private final HelpfulSignalQueryService helpfulSignalQueryService;

  public ReviewRankingInputService(HelpfulSignalQueryService helpfulSignalQueryService) {
    this.helpfulSignalQueryService =
        Objects.requireNonNull(helpfulSignalQueryService, "helpfulSignalQueryService");
  }

  /**
   * Helpfulness inputs for reviews the caller has <em>already</em> been authorised to see.
   *
   * <p>Like the count query it builds on, this performs no visibility check of its own: it
   * decorates reviews a caller is holding, which were resolved through {@link ReviewQueryService}.
   * It must never be used to turn an unchecked identifier into information.
   *
   * <p>Every requested review gets an entry, including those nothing has signalled — a ranking
   * layer needs a value for everything it scores, and a missing entry would invite a caller to
   * invent a default that disagrees with this policy.
   */
  public Map<ReviewId, HelpfulnessInput> forVisibleReviews(Collection<ReviewId> reviewIds) {
    Objects.requireNonNull(reviewIds, "reviewIds");
    Map<ReviewId, Long> activeCounts =
        helpfulSignalQueryService.activeCountsForVisibleReviews(reviewIds);
    RankingInputVersion version = RankingInputVersion.current();

    Map<ReviewId, HelpfulnessInput> inputs = new HashMap<>();
    for (ReviewId reviewId : reviewIds) {
      long activeSignals = activeCounts.getOrDefault(reviewId, 0L);
      inputs.put(reviewId, HelpfulnessInputPolicy.forActiveSignals(activeSignals, version));
    }
    return Map.copyOf(inputs);
  }
}
