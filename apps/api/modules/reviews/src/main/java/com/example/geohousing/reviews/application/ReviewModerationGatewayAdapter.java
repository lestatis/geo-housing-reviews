package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.api.ModeratableReview;
import com.example.geohousing.reviews.api.ReviewModerationConflictException;
import com.example.geohousing.reviews.api.ReviewModerationEffect;
import com.example.geohousing.reviews.api.ReviewModerationGateway;
import com.example.geohousing.reviews.domain.ModeratorId;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewId;
import com.example.geohousing.reviews.domain.ReviewStatus;
import com.example.geohousing.reviews.domain.ReviewVersionConflictException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Answers the published {@link ReviewModerationGateway} from this module's own moderation service.
 *
 * <p>It adds no moderation logic. Every state change goes through {@link ReviewModerationService},
 * so the stale-version check and the audit row that is committed with the mutation apply exactly as
 * they do for this module's own admin endpoints — an outside caller gets no cheaper path to the
 * same effect.
 */
public final class ReviewModerationGatewayAdapter implements ReviewModerationGateway {

  private final ReviewRepository reviewRepository;
  private final ReviewModerationService moderationService;

  public ReviewModerationGatewayAdapter(
      ReviewRepository reviewRepository, ReviewModerationService moderationService) {
    this.reviewRepository = Objects.requireNonNull(reviewRepository, "reviewRepository");
    this.moderationService = Objects.requireNonNull(moderationService, "moderationService");
  }

  @Override
  public Optional<ModeratableReview> find(UUID reviewId) {
    Objects.requireNonNull(reviewId, "reviewId");
    return reviewRepository
        .findById(ReviewId.of(reviewId))
        .map(ReviewModerationGatewayAdapter::toModeratable);
  }

  @Override
  public boolean apply(
      UUID reviewId,
      ReviewModerationEffect effect,
      long expectedVersion,
      UUID moderatorAccountId,
      String reasonCode) {
    Objects.requireNonNull(reviewId, "reviewId");
    Objects.requireNonNull(effect, "effect");
    Objects.requireNonNull(moderatorAccountId, "moderatorAccountId");

    ReviewId id = ReviewId.of(reviewId);
    ModeratorId moderator = ModeratorId.of(moderatorAccountId);
    try {
      Optional<Review> affected =
          switch (effect) {
            case PUBLISH -> moderationService.publish(moderator, id, expectedVersion, reasonCode);
            case REJECT -> moderationService.reject(moderator, id, expectedVersion, reasonCode);
            case HIDE -> moderationService.hide(moderator, id, expectedVersion, reasonCode);
            case RESTORE -> moderationService.restore(moderator, id, expectedVersion, reasonCode);
            case REMOVE -> moderationService.remove(moderator, id, expectedVersion, reasonCode);
          };
      return affected.isPresent();
    } catch (ReviewVersionConflictException conflict) {
      // Translated rather than propagated: the domain exception is this module's internal type, and
      // letting it cross would make callers catch our internals.
      throw new ReviewModerationConflictException(conflict.getMessage());
    }
  }

  private static ModeratableReview toModeratable(Review review) {
    return new ModeratableReview(
        review.id().value(),
        review.authorId().value(),
        review.version(),
        review.status() == ReviewStatus.PUBLISHED);
  }
}
