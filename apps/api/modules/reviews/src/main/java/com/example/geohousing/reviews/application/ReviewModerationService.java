package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.ModeratorId;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewId;
import com.example.geohousing.reviews.domain.ReviewModerationAction;
import com.example.geohousing.reviews.domain.ReviewModerationAuditEvent;
import com.example.geohousing.reviews.domain.ReviewVersion;
import com.example.geohousing.reviews.domain.ReviewVersionConflictException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Applies moderation decisions to reviews. Every action — including one against a review that does
 * not exist — is audited with its reason code; the mutation and the audit row are committed
 * together by the {@link ReviewModerationRepository}.
 *
 * <p>{@code expectedVersion} is the review version the moderator saw. A mismatch is refused before
 * anything happens, so a moderator never acts on content that changed under them — the moderation
 * equivalent of the stale-content rule in API_GUIDELINES.
 *
 * <p>This service owns the publication state only. Reports, appeals, redaction and the reason-code
 * taxonomy belong to the moderation module; the verification tier belongs to the verification
 * module.
 */
public final class ReviewModerationService {

  private final ReviewRepository reviewRepository;
  private final ReviewModerationRepository moderationRepository;
  private final Clock clock;

  public ReviewModerationService(
      ReviewRepository reviewRepository,
      ReviewModerationRepository moderationRepository,
      Clock clock) {
    this.reviewRepository = Objects.requireNonNull(reviewRepository, "reviewRepository");
    this.moderationRepository =
        Objects.requireNonNull(moderationRepository, "moderationRepository");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Publishes a review awaiting moderation. */
  public Optional<Review> publish(
      ModeratorId moderatorId, ReviewId reviewId, long expectedVersion, String reasonCode) {
    return apply(
        moderatorId,
        ReviewModerationAction.PUBLISH,
        reviewId,
        expectedVersion,
        reasonCode,
        Review::publish);
  }

  /** Rejects a review awaiting moderation (terminal; the author may start a fresh review). */
  public Optional<Review> reject(
      ModeratorId moderatorId, ReviewId reviewId, long expectedVersion, String reasonCode) {
    return apply(
        moderatorId,
        ReviewModerationAction.REJECT,
        reviewId,
        expectedVersion,
        reasonCode,
        Review::reject);
  }

  /** Temporarily withholds a published review from public view. */
  public Optional<Review> hide(
      ModeratorId moderatorId, ReviewId reviewId, long expectedVersion, String reasonCode) {
    return apply(
        moderatorId,
        ReviewModerationAction.HIDE,
        reviewId,
        expectedVersion,
        reasonCode,
        Review::hide);
  }

  /** Restores a hidden review to public view. */
  public Optional<Review> restore(
      ModeratorId moderatorId, ReviewId reviewId, long expectedVersion, String reasonCode) {
    return apply(
        moderatorId,
        ReviewModerationAction.RESTORE,
        reviewId,
        expectedVersion,
        reasonCode,
        Review::restore);
  }

  /** Removes a review permanently (terminal). */
  public Optional<Review> remove(
      ModeratorId moderatorId, ReviewId reviewId, long expectedVersion, String reasonCode) {
    return apply(
        moderatorId,
        ReviewModerationAction.REMOVE,
        reviewId,
        expectedVersion,
        reasonCode,
        Review::remove);
  }

  private Optional<Review> apply(
      ModeratorId moderatorId,
      ReviewModerationAction action,
      ReviewId reviewId,
      long expectedVersion,
      String reasonCode,
      BiConsumer<Review, Clock> transition) {
    Objects.requireNonNull(moderatorId, "moderatorId");
    Objects.requireNonNull(reviewId, "reviewId");
    if (reasonCode == null || reasonCode.isBlank()) {
      // Checked before anything else happens (the audit event enforces it too): an action without
      // a reason must fail before any state is touched, not midway.
      throw new IllegalArgumentException("every moderation action requires a reason code");
    }
    Instant now = clock.instant();

    Optional<Review> found = reviewRepository.findById(reviewId);
    if (found.isEmpty()) {
      moderationRepository.recordAttempt(
          ReviewModerationAuditEvent.notFound(moderatorId, action, reviewId, reasonCode, now));
      return Optional.empty();
    }

    Review review = found.get();
    if (review.version() != expectedVersion) {
      throw new ReviewVersionConflictException(
          "review " + reviewId.value() + " changed since the moderator loaded it");
    }
    transition.accept(review, clock);
    moderationRepository.applyDecision(
        review,
        ReviewModerationAuditEvent.applied(
            moderatorId,
            action,
            reviewId,
            review.currentVersion().map(ReviewVersion::id).orElse(null),
            reasonCode,
            now));
    return Optional.of(review);
  }
}
