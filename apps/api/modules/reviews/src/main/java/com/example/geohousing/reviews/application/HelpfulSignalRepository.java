package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.HelpfulSignal;
import com.example.geohousing.reviews.domain.HelpfulSignalVoterId;
import com.example.geohousing.reviews.domain.ReviewId;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Persistence port for private helpful signals. */
public interface HelpfulSignalRepository {

  Optional<HelpfulSignal> findActive(ReviewId reviewId, HelpfulSignalVoterId voterId);

  void create(HelpfulSignal signal);

  void withdraw(HelpfulSignal signal);

  /** Active-signal total for one review. This projection never returns voter identities. */
  long countActive(ReviewId reviewId);

  /**
   * Active-signal totals for several reviews at once, so rendering a page of reviews costs one
   * query rather than one per review — the public listing is an anonymous read path and must not
   * degrade as a property accumulates reviews.
   *
   * <p>Reviews with no active signals may be absent from the result; callers read a missing entry
   * as zero. Like {@link #countActive}, this returns totals only and never voter identities.
   */
  Map<ReviewId, Long> countActive(Collection<ReviewId> reviewIds);
}
