package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.HelpfulSignal;
import com.example.geohousing.reviews.domain.HelpfulSignalVoterId;
import com.example.geohousing.reviews.domain.ReviewId;
import java.util.Optional;

/** Persistence port for private helpful signals. */
public interface HelpfulSignalRepository {

  Optional<HelpfulSignal> findActive(ReviewId reviewId, HelpfulSignalVoterId voterId);

  void create(HelpfulSignal signal);

  void withdraw(HelpfulSignal signal);

  /** Active-signal total for one review. This projection never returns voter identities. */
  long countActive(ReviewId reviewId);
}
