package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.HelpfulSignal;
import com.example.geohousing.reviews.domain.HelpfulSignalId;
import com.example.geohousing.reviews.domain.HelpfulSignalVoterId;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewId;
import com.example.geohousing.reviews.domain.ReviewNotFoundException;
import com.example.geohousing.reviews.domain.ReviewStatus;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Adds and withdraws private helpful signals for published reviews.
 *
 * <p>Unpublished reviews are reported as missing even to an authenticated voter. A helpful-signal
 * action must not become a way to probe drafts, moderation queue entries, hidden reviews, or
 * removed content. A review author is refused separately because they already know the review
 * exists and self-signalling would be trivial ranking manipulation.
 */
public final class HelpfulSignalService {

  private final ReviewRepository reviewRepository;
  private final HelpfulSignalRepository helpfulSignalRepository;
  private final Clock clock;

  public HelpfulSignalService(
      ReviewRepository reviewRepository,
      HelpfulSignalRepository helpfulSignalRepository,
      Clock clock) {
    this.reviewRepository = Objects.requireNonNull(reviewRepository, "reviewRepository");
    this.helpfulSignalRepository =
        Objects.requireNonNull(helpfulSignalRepository, "helpfulSignalRepository");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Adds a new active positive signal. A duplicate active signal is a conflict, never a count. */
  public HelpfulSignal add(ReviewId reviewId, HelpfulSignalVoterId voterId) {
    Review review = requireEligibleReview(reviewId, voterId);
    helpfulSignalRepository
        .findActive(reviewId, voterId)
        .ifPresent(
            existing -> {
              throw new HelpfulSignalAlreadyActiveException(reviewId);
            });

    HelpfulSignal signal =
        HelpfulSignal.create(HelpfulSignalId.of(UUID.randomUUID()), review.id(), voterId, clock);
    helpfulSignalRepository.create(signal);
    return signal;
  }

  /**
   * Withdraws the caller's active signal. A missing active signal is an idempotent no-op, suitable
   * for a future DELETE endpoint.
   *
   * @return {@code true} when a signal was withdrawn; {@code false} when none was active
   */
  public boolean withdraw(ReviewId reviewId, HelpfulSignalVoterId voterId) {
    requireEligibleReview(reviewId, voterId);
    return helpfulSignalRepository
        .findActive(reviewId, voterId)
        .map(
            signal -> {
              signal.withdraw(clock);
              helpfulSignalRepository.withdraw(signal);
              return true;
            })
        .orElse(false);
  }

  private Review requireEligibleReview(ReviewId reviewId, HelpfulSignalVoterId voterId) {
    Objects.requireNonNull(reviewId, "reviewId");
    Objects.requireNonNull(voterId, "voterId");
    Review review =
        reviewRepository
            .findById(reviewId)
            .filter(found -> found.status() == ReviewStatus.PUBLISHED)
            .orElseThrow(() -> new ReviewNotFoundException(reviewId));
    if (review.authorId().value().equals(voterId.value())) {
      throw new SelfHelpfulSignalException(reviewId);
    }
    return review;
  }
}
